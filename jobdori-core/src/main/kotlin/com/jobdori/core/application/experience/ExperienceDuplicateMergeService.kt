package com.jobdori.core.application.experience

import com.jobdori.common.logger.LoggerExtension.log
import com.jobdori.common.model.Period
import com.jobdori.core.application.ai.client.AiChatClient
import com.jobdori.core.application.experience.command.ImportedExperienceCommandGroup
import com.jobdori.core.domain.ai.error.AiErrorCode
import com.jobdori.core.domain.ai.error.AiException
import com.jobdori.core.domain.experience.Experience
import com.jobdori.core.domain.experience.ExperienceContents
import com.jobdori.core.domain.experience.ExperiencePolicy
import com.jobdori.core.domain.experience.ExperienceProject
import com.jobdori.core.domain.experience.FreeExperienceContents
import com.jobdori.core.domain.experience.StarExperienceContents
import com.jobdori.core.domain.experience.service.ExperienceCreator
import com.jobdori.core.domain.experience.service.ExperienceModifier
import com.jobdori.core.domain.experience.service.ExperienceProjectModifier
import com.jobdori.core.domain.experience.service.ExperienceProjectReader
import com.jobdori.core.domain.experience.service.ExperienceReader
import com.jobdori.core.domain.experience.service.command.ExperienceCreateCommand
import com.jobdori.core.domain.experience.service.command.ExperienceProjectCreateCommand
import com.jobdori.core.domain.prompt.PromptType
import com.jobdori.core.domain.prompt.repository.PromptTemplateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExperienceDuplicateMergeService(
    private val experienceProjectReader: ExperienceProjectReader,
    private val experienceReader: ExperienceReader,
    private val promptTemplateRepository: PromptTemplateRepository,
    private val aiChatClient: AiChatClient,
    private val experienceImportService: ExperienceImportService,
    private val experienceProjectModifier: ExperienceProjectModifier,
    private val experienceModifier: ExperienceModifier,
    private val experienceCreator: ExperienceCreator,
) {

    fun plan(workspaceId: Long, groups: List<ImportedExperienceCommandGroup>): ImportMergePlan {
        if (groups.isEmpty()) {
            return ImportMergePlan(emptyList())
        }

        val projects = experienceProjectReader.getProjects(
            workspaceId = workspaceId,
            cursor = null,
            size = ExperiencePolicy.MAX_PROJECT_COUNT,
        ).items
        if (projects.isEmpty()) {
            return groups.toNewPlan()
        }

        val projectMatches = matchProjects(projects, groups)
        val experiencesByProjectId = if (projectMatches.any { it.targetProjectId != null }) {
            experienceReader.findAllActive(workspaceId).groupBy(Experience::projectId)
        } else {
            emptyMap()
        }

        return ImportMergePlan(
            groups = projectMatches.zip(groups).flatMap { (projectPlan, group) ->
                val projectId = projectPlan.targetProjectId ?: return@flatMap listOf(projectPlan)
                val existingExperiences = experiencesByProjectId[projectId].orEmpty()
                splitByProjectCapacity(
                    projectPlan = projectPlan.copy(
                        experiences = matchExperiencesOrKeep(
                            workspaceId = workspaceId,
                            projectId = projectId,
                            existingExperiences = existingExperiences,
                            newExperiences = group.experiences,
                            fallback = projectPlan.experiences,
                        ),
                    ),
                    sourceProject = group.project,
                    existingExperienceCount = existingExperiences.size,
                )
            },
        )
    }

    @Transactional
    fun save(workspaceId: Long, plan: ImportMergePlan) {
        val newGroups = plan.groups.filter { it.targetProjectId == null }.map { groupPlan ->
            ImportedExperienceCommandGroup(
                project = groupPlan.project,
                experiences = groupPlan.experiences.map { it.command },
            )
        }
        if (newGroups.isNotEmpty()) {
            experienceImportService.saveAll(workspaceId, newGroups)
        }

        plan.groups.filter { it.targetProjectId != null }.forEach { groupPlan ->
            val projectId = checkNotNull(groupPlan.targetProjectId)
            experienceProjectModifier.modify(
                workspaceId = workspaceId,
                projectId = projectId,
                name = groupPlan.project.name,
                summary = groupPlan.project.summary,
                period = groupPlan.project.period,
                role = groupPlan.project.role,
            )

            val newExperiences = groupPlan.experiences.filter { it.targetExperienceId == null }
                .map { it.command }
                .map { experience ->
                    experience.copy(
                        period = experience.period ?: groupPlan.project.period,
                        role = experience.role ?: groupPlan.project.role,
                    )
                }
            experienceCreator.create(
                workspaceId = workspaceId,
                projectId = projectId,
                commands = newExperiences,
            )
            groupPlan.experiences.filter { it.targetExperienceId != null }.forEach { experiencePlan ->
                val command = experiencePlan.command
                experienceModifier.modify(
                    workspaceId = workspaceId,
                    experienceId = checkNotNull(experiencePlan.targetExperienceId),
                    projectId = projectId,
                    tags = command.tags,
                    title = command.title,
                    contents = command.contents,
                    period = command.period,
                    role = command.role,
                )
            }
        }

        val mergedProjectCount = plan.groups.count { it.targetProjectId != null }
        val mergedExperienceCount = plan.groups.sumOf { group ->
            group.experiences.count { it.targetExperienceId != null }
        }
        log.info {
            "경험 임포트 저장 완료: workspaceId=$workspaceId, 생성 프로젝트=${plan.groups.size - mergedProjectCount}, " +
                "병합 프로젝트=$mergedProjectCount, " +
                "생성 경험=${plan.groups.sumOf { it.experiences.size } - mergedExperienceCount}, " +
                "병합 경험=$mergedExperienceCount"
        }
    }

    private fun matchExperiencesOrKeep(
        workspaceId: Long,
        projectId: Long,
        existingExperiences: List<Experience>,
        newExperiences: List<ExperienceCreateCommand>,
        fallback: List<ImportMergePlan.ExperiencePlan>,
    ): List<ImportMergePlan.ExperiencePlan> {
        if (existingExperiences.isEmpty()) {
            return fallback
        }
        return runCatching { matchExperiences(existingExperiences, newExperiences) }
            .onFailure { e ->
                log.warn(e) {
                    "경험 중복 판정 실패, 프로젝트 매칭만 유지: " +
                        "workspaceId=$workspaceId, projectId=$projectId"
                }
            }
            .getOrElse { fallback }
    }

    private fun splitByProjectCapacity(
        projectPlan: ImportMergePlan.GroupPlan,
        sourceProject: ExperienceProjectCreateCommand,
        existingExperienceCount: Int,
    ): List<ImportMergePlan.GroupPlan> {
        var remainingSlots = (ExperiencePolicy.MAX_EXPERIENCE_COUNT_PER_PROJECT - existingExperienceCount)
            .coerceAtLeast(0)
        val retained = mutableListOf<ImportMergePlan.ExperiencePlan>()
        val overflow = mutableListOf<ImportMergePlan.ExperiencePlan>()

        projectPlan.experiences.forEach { experiencePlan ->
            if (experiencePlan.targetExperienceId != null) {
                retained += experiencePlan
            } else if (remainingSlots > 0) {
                retained += experiencePlan
                remainingSlots--
            } else {
                overflow += experiencePlan
            }
        }

        val retainedProject = projectPlan.copy(experiences = retained)
        return if (overflow.isEmpty()) {
            listOf(retainedProject)
        } else {
            listOf(
                retainedProject,
                ImportMergePlan.GroupPlan(
                    targetProjectId = null,
                    project = sourceProject,
                    experiences = overflow,
                ),
            )
        }
    }

    private fun matchProjects(
        projects: List<ExperienceProject>,
        groups: List<ImportedExperienceCommandGroup>,
    ): List<ImportMergePlan.GroupPlan> {
        val template = promptTemplateRepository.findByType(PromptType.EXPERIENCE_PROJECT_DUPLICATE_MATCH)
            ?: throw AiException("경험 프로젝트 중복 판정 프롬프트를 찾을 수 없습니다.", AiErrorCode.E500_AI_GENERATION_FAILED)
        val result = aiChatClient.generateStructured(
            template.buildStructured(
                userPrompt = buildProjectPrompt(projects, groups),
                responseType = ProjectDuplicateMatchResult::class,
            ),
        )
        val projectsById = projects.associateBy(ExperienceProject::id)
        val matchesByIndex = result.items.groupBy(ProjectDuplicateMatchItem::index)
            .mapValues { (_, matches) -> matches.first() }
        val usedProjectIds = mutableSetOf<Long>()

        return groups.mapIndexed { zeroBasedIndex, group ->
            val match = matchesByIndex[zeroBasedIndex + 1]
            val project = match?.matchedProjectId?.let(projectsById::get)
                ?.takeIf { usedProjectIds.add(it.id) }
            if (project == null) {
                group.toNewPlan()
            } else {
                ImportMergePlan.GroupPlan(
                    targetProjectId = project.id,
                    project = ExperienceProjectCreateCommand(
                        name = project.name,
                        summary = firstNonBlank(
                            match.summary,
                            group.project.summary,
                            project.summary,
                            limit = MAX_PROJECT_SUMMARY_LENGTH,
                        ),
                        period = match.period.toPeriod() ?: group.project.period ?: project.period,
                        role = firstNonBlank(
                            match.role,
                            group.project.role.orEmpty(),
                            project.role.orEmpty(),
                            limit = MAX_ROLE_LENGTH,
                        ).ifBlank { null },
                    ),
                    experiences = group.experiences.map { command ->
                        ImportMergePlan.ExperiencePlan(targetExperienceId = null, command = command)
                    },
                )
            }
        }
    }

    private fun matchExperiences(
        existingExperiences: List<Experience>,
        newExperiences: List<ExperienceCreateCommand>,
    ): List<ImportMergePlan.ExperiencePlan> {
        val template = promptTemplateRepository.findByType(PromptType.EXPERIENCE_DUPLICATE_MERGE)
            ?: throw AiException("경험 중복 판정 프롬프트를 찾을 수 없습니다.", AiErrorCode.E500_AI_GENERATION_FAILED)
        val result = aiChatClient.generateStructured(
            template.buildStructured(
                userPrompt = buildExperiencePrompt(existingExperiences, newExperiences),
                responseType = ExperienceDuplicateMergeResult::class,
            ),
        )
        val experiencesById = existingExperiences.associateBy(Experience::id)
        val matchesByIndex = result.items.groupBy(ExperienceDuplicateMergeItem::index)
            .mapValues { (_, matches) -> matches.first() }
        val usedExperienceIds = mutableSetOf<Long>()

        return newExperiences.mapIndexed { zeroBasedIndex, command ->
            val match = matchesByIndex[zeroBasedIndex + 1]
            val experience = match?.matchedExperienceId?.let(experiencesById::get)
                ?.takeIf { usedExperienceIds.add(it.id) }
            if (experience == null) {
                ImportMergePlan.ExperiencePlan(targetExperienceId = null, command = command)
            } else {
                val previous = experience.contents.toStarFallback()
                val imported = command.contents.toStarFallback()
                ImportMergePlan.ExperiencePlan(
                    targetExperienceId = experience.id,
                    command = ExperienceCreateCommand(
                        // 기존 태그를 앞에 둬야 MAX_TAG_COUNT에서 잘릴 때 살아남는 태그가 고정된다.
                        // AI 순서를 우선하면 재임포트마다 잘리는 태그가 바뀐다.
                        tags = (
                            experience.tags +
                                match.tags.map(String::trim).filter(String::isNotBlank) +
                                command.tags
                            // 공백만 다른 표기("서비스 기획" / "서비스기획")를 같은 태그로 본다. 앞선 표기가 살아남는다.
                            ).distinctBy { it.filterNot(Char::isWhitespace) }.take(MAX_TAG_COUNT),
                        title = firstNonBlank(
                            match.title,
                            command.title,
                            experience.title,
                            limit = MAX_EXPERIENCE_TITLE_LENGTH,
                        ),
                        contents = ExperienceContents.star(
                            situation = firstNonBlank(match.situation, imported.situation, previous.situation),
                            task = firstNonBlank(match.task, imported.task, previous.task),
                            action = firstNonBlank(match.action, imported.action, previous.action),
                            result = firstNonBlank(match.result, imported.result, previous.result),
                        ),
                        period = match.period.toPeriod() ?: command.period ?: experience.period,
                        role = firstNonBlank(
                            match.role,
                            command.role.orEmpty(),
                            experience.role.orEmpty(),
                            limit = MAX_ROLE_LENGTH,
                        ).ifBlank { null },
                    ),
                )
            }
        }
    }

    private fun buildProjectPrompt(
        projects: List<ExperienceProject>,
        groups: List<ImportedExperienceCommandGroup>,
    ): String = buildString {
        appendLine("[기존 프로젝트]")
        projects.forEach { project ->
            appendLine(
                "projectId=${project.id}, name=${project.name}, summary=${project.summary}, " +
                    "period=${project.period.toPrompt()}, role=${project.role.orEmpty()}",
            )
        }
        appendLine("[새 프로젝트]")
        groups.forEachIndexed { index, group ->
            val project = group.project
            appendLine(
                "index=${index + 1}, name=${project.name}, summary=${project.summary}, " +
                    "period=${project.period.toPrompt()}, role=${project.role.orEmpty()}",
            )
        }
    }

    private fun buildExperiencePrompt(
        existingExperiences: List<Experience>,
        newExperiences: List<ExperienceCreateCommand>,
    ): String = buildString {
        appendLine("[기존 경험]")
        existingExperiences.forEach { experience ->
            appendLine(experience.toPrompt("experienceId=${experience.id}"))
        }
        appendLine("[새 경험]")
        newExperiences.forEachIndexed { index, experience ->
            appendLine(experience.toPrompt("index=${index + 1}"))
        }
    }

    private fun ImportedExperienceCommandGroup.toNewPlan() = ImportMergePlan.GroupPlan(
        targetProjectId = null,
        project = project,
        experiences = experiences.map { command ->
            ImportMergePlan.ExperiencePlan(targetExperienceId = null, command = command)
        },
    )

    private fun List<ImportedExperienceCommandGroup>.toNewPlan() = ImportMergePlan(map { it.toNewPlan() })

    private fun Experience.toPrompt(prefix: String): String =
        "$prefix, title=$title, tags=${tags.joinToString(",")}, period=${period.toPrompt()}, " +
            "role=${role.orEmpty()}, ${contents.toPrompt()}"

    private fun ExperienceCreateCommand.toPrompt(prefix: String): String =
        "$prefix, title=$title, tags=${tags.joinToString(",")}, period=${period.toPrompt()}, " +
            "role=${role.orEmpty()}, ${contents.toPrompt()}"

    private fun ExperienceContents.toPrompt(): String = when (this) {
        is StarExperienceContents ->
            "situation=$situation, task=$task, action=$action, result=$result"
        is FreeExperienceContents -> "content=$content"
    }

    private fun ExperienceContents.toStarFallback(): StarExperienceContents = when (this) {
        is StarExperienceContents -> this
        is FreeExperienceContents -> StarExperienceContents(content, "", "", "")
    }

    private fun firstNonBlank(vararg candidates: String, limit: Int = Int.MAX_VALUE): String =
        candidates.firstOrNull(String::isNotBlank).orEmpty().trim().take(limit)

    private fun Period?.toPrompt(): String = this?.let {
        "${it.startAt?.toString().orEmpty()}~${it.endAt?.toString().orEmpty()}"
    }.orEmpty()
}

data class ImportMergePlan(
    val groups: List<GroupPlan>,
) {
    data class GroupPlan(
        val targetProjectId: Long?,
        val project: ExperienceProjectCreateCommand,
        val experiences: List<ExperiencePlan>,
    )

    data class ExperiencePlan(
        val targetExperienceId: Long?,
        val command: ExperienceCreateCommand,
    )
}

internal data class ProjectDuplicateMatchResult(
    val items: List<ProjectDuplicateMatchItem> = emptyList(),
)

internal data class ProjectDuplicateMatchItem(
    val index: Int,
    val matchedProjectId: Long? = null,
    val summary: String = "",
    val role: String = "",
    val period: ExtractedPeriod = ExtractedPeriod(),
)

internal data class ExperienceDuplicateMergeResult(
    val items: List<ExperienceDuplicateMergeItem> = emptyList(),
)

internal data class ExperienceDuplicateMergeItem(
    val index: Int,
    val matchedExperienceId: Long? = null,
    val title: String = "",
    val tags: List<String> = emptyList(),
    val role: String = "",
    val period: ExtractedPeriod = ExtractedPeriod(),
    val situation: String = "",
    val task: String = "",
    val action: String = "",
    val result: String = "",
)

private const val MAX_PROJECT_SUMMARY_LENGTH = 500
private const val MAX_EXPERIENCE_TITLE_LENGTH = 150
private const val MAX_ROLE_LENGTH = 100
private const val MAX_TAG_COUNT = 10
