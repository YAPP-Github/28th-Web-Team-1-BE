package com.jobdori.api.application.experience.service

import com.jobdori.common.logger.LoggerExtension.log
import com.jobdori.core.application.experience.ExperienceAiExtractionService
import com.jobdori.core.application.experience.ExperienceDuplicateMergeService
import com.jobdori.core.application.experience.ExperienceImportService
import com.jobdori.core.application.profile.ExperienceCoreCompetencyService
import com.jobdori.core.domain.experience.error.ExperieneceEmptyImportedException
import com.jobdori.core.domain.experience.service.ExperienceReader
import com.jobdori.core.domain.profile.service.ProfileModifier
import com.jobdori.core.domain.profile.service.ProfileReader
import org.springframework.stereotype.Service

@Service
class ExperienceTextImportService(
    private val experienceImportService: ExperienceImportService,
    private val experienceAiExtractionService: ExperienceAiExtractionService,
    private val experienceDuplicateMergeService: ExperienceDuplicateMergeService,
    private val profileReader: ProfileReader,
    private val profileModifier: ProfileModifier,
    private val experienceReader: ExperienceReader,
    private val experienceCoreCompetencyService: ExperienceCoreCompetencyService,
) {

    fun import(workspaceId: Long, text: String) {
        val result = experienceAiExtractionService.extract(text)

        val commands = result.toCommandGroups()

        if (commands.isEmpty()) {
            throw ExperieneceEmptyImportedException(
                message = "불러온 곳에 경험이 비어있습니다 [workspaceId=$workspaceId,text=$text",
            )
        }

        val plan = runCatching {
            experienceDuplicateMergeService.plan(workspaceId, commands)
        }.onFailure { e ->
            log.warn(e) { "경험 중복 판정 실패, 중복 검사 없이 임포트 진행: workspaceId=$workspaceId" }
        }.getOrNull()

        if (plan == null) {
            experienceImportService.saveAll(workspaceId = workspaceId, groups = commands)
        } else {
            experienceDuplicateMergeService.save(workspaceId, plan)
        }

        val profile = profileReader.getOrCreateProfile(workspaceId)
        profileModifier.modify(profile, result.toProfileUpdateCommand(profileReader.getDetail(profile)))

        runCatching {
            experienceCoreCompetencyService.generate(
                workspaceId = workspaceId,
                experiences = experienceReader.findAllActive(workspaceId),
            )
        }.onFailure { e ->
            log.warn(e) { "이력서 임포트 핵심역량 생성 실패, 임포트는 유지: workspaceId=$workspaceId" }
        }
    }

}
