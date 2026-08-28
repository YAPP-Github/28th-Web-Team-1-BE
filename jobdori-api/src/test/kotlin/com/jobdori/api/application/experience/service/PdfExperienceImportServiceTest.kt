package com.jobdori.api.application.experience.service

import com.jobdori.api.application.workspace.service.WorkspaceAccessValidationService
import com.jobdori.common.error.InvalidArgumentsException
import com.jobdori.core.application.experience.ExperienceAiExtractionService
import com.jobdori.core.application.ai.client.DocumentVisionClient
import com.jobdori.core.application.experience.ExperienceDuplicateMergeService
import com.jobdori.core.application.experience.ExperienceImportService
import com.jobdori.core.application.experience.ExperienceStarExtractionResult
import com.jobdori.core.application.experience.command.ImportedExperienceCommandGroup
import com.jobdori.core.application.profile.ExperienceCoreCompetencyService
import com.jobdori.core.domain.experience.ExperienceContents
import com.jobdori.core.domain.experience.service.ExperienceReader
import com.jobdori.core.domain.experience.service.command.ExperienceCreateCommand
import com.jobdori.core.domain.experience.service.command.ExperienceProjectCreateCommand
import com.jobdori.core.domain.profile.Profile
import com.jobdori.core.domain.profile.ProfileDetail
import com.jobdori.core.domain.profile.ProfileSections
import com.jobdori.core.domain.profile.service.ProfileModifier
import com.jobdori.core.domain.profile.service.ProfileReader
import com.jobdori.core.domain.profile.service.command.ProfileUpdateCommand
import com.jobdori.core.domain.prompt.PromptTemplate
import com.jobdori.core.domain.prompt.PromptType
import com.jobdori.core.domain.prompt.repository.PromptTemplateRepository
import com.jobdori.core.application.ai.command.AiParameters
import com.jobdori.core.domain.workspace.Workspace
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import java.io.ByteArrayOutputStream

internal class PdfExperienceImportServiceTest : StringSpec({

    val experienceImportService = mockk<ExperienceImportService>()
    val experienceAiExtractionService = mockk<ExperienceAiExtractionService>()
    val experienceDuplicateMergeService = mockk<ExperienceDuplicateMergeService>()
    val workspaceAccessValidationService = mockk<WorkspaceAccessValidationService>()
    val pdfValidationService = mockk<PdfValidationService>()
    val profileReader = mockk<ProfileReader>()
    val profileModifier = mockk<ProfileModifier>()
    val experienceReader = mockk<ExperienceReader>()
    val experienceCoreCompetencyService = mockk<ExperienceCoreCompetencyService>()
    val documentVisionClient = mockk<DocumentVisionClient>()
    val promptTemplateRepository = mockk<PromptTemplateRepository>()
    val experienceTextImportService = ExperienceTextImportService(
        experienceImportService = experienceImportService,
        experienceAiExtractionService = experienceAiExtractionService,
        experienceDuplicateMergeService = experienceDuplicateMergeService,
        profileReader = profileReader,
        profileModifier = profileModifier,
        experienceReader = experienceReader,
        experienceCoreCompetencyService = experienceCoreCompetencyService,
    )
    val service = PdfExperienceImportService(
        workspaceAccessValidationService = workspaceAccessValidationService,
        pdfValidationService = pdfValidationService,
        experienceTextImportService = experienceTextImportService,
        documentVisionClient = documentVisionClient,
        promptTemplateRepository = promptTemplateRepository,
    )

    beforeTest {
        clearMocks(
            experienceImportService,
            experienceAiExtractionService,
            experienceDuplicateMergeService,
            workspaceAccessValidationService,
            pdfValidationService,
            profileReader,
            profileModifier,
            experienceReader,
            experienceCoreCompetencyService,
            documentVisionClient,
            promptTemplateRepository,
        )
        every {
            workspaceAccessValidationService.validateAccessible(
                workspaceId = "workspace-id",
                userId = 1L,
            )
        } returns Workspace(id = 1L, publicId = "workspace-id", ownerUserId = 1L)
    }

    "유효한 PDF는 파일 스캔 후 가져온 경험을 저장한다" {
        val pdfBytes = samplePdfBytes("Hello Jobdori resume experience text with enough meaningful characters")
        val file = MockMultipartFile(
            "file",
            "resume.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            pdfBytes,
        )

        every { pdfValidationService.validate(file = file, userId = 1L) } returns pdfBytes
        val groups = listOf(
            ImportedExperienceCommandGroup(
                project = ExperienceProjectCreateCommand(
                    name = "PDF 경험 추출",
                    summary = "PDF에서 추출한 경험",
                    period = null,
                    role = null,
                ),
                experiences = listOf(
                    ExperienceCreateCommand(
                        tags = listOf("PDF"),
                        title = "PDF 텍스트 추출",
                        contents = ExperienceContents.free("PDF 텍스트를 추출했다"),
                    ),
                ),
            ),
        )
        val extractionResult = mockk<ExperienceStarExtractionResult> {
            every { toCommandGroups() } returns groups
            every { toProfileUpdateCommand(any()) } returns ProfileUpdateCommand()
        }
        every { experienceAiExtractionService.extract(any()) } returns extractionResult
        every { experienceImportService.saveAll(workspaceId = 1L, groups = groups) } returns Unit
        val profile = Profile.newInstance(workspaceId = 1L)
        val profileDetail = ProfileDetail(profile = profile, sections = emptyProfileSections())
        every { profileReader.getOrCreateProfile(1L) } returns profile
        every { profileReader.getDetail(profile) } returns profileDetail
        every { profileModifier.modify(profile, any()) } returns profileDetail
        every { experienceReader.findAllActive(1L) } returns emptyList()
        every {
            experienceCoreCompetencyService.generate(1L, emptyList())
        } returns Unit

        service.importExperiences(file = file, workspaceId = "workspace-id", userId = 1L)

        verify(exactly = 1) { pdfValidationService.validate(file = file, userId = 1L) }
        verify(exactly = 1) { experienceAiExtractionService.extract(match { text -> text.contains("Hello Jobdori") }) }
        verify(exactly = 1) { experienceImportService.saveAll(workspaceId = 1L, groups = groups) }
        verify(exactly = 1) { profileModifier.modify(profile, any()) }
        verify(exactly = 0) { documentVisionClient.extractText(any(), any()) }
    }

    "PDF 검증에 실패하면 가져온 경험을 저장하지 않는다" {
        val file = MockMultipartFile(
            "file",
            "resume.pdf",
            MediaType.TEXT_PLAIN_VALUE,
            samplePdfBytes("Hello Jobdori"),
        )

        every {
            pdfValidationService.validate(file = file, userId = 1L)
        } throws InvalidArgumentsException(message = "유효한 PDF 파일을 첨부해 주세요")

        val exception = shouldThrow<InvalidArgumentsException> {
            service.importExperiences(file = file, workspaceId = "workspace-id", userId = 1L)
        }

        exception.message shouldBe "유효한 PDF 파일을 첨부해 주세요"
        verify(exactly = 1) { pdfValidationService.validate(file = file, userId = 1L) }
        verify(exactly = 0) { experienceAiExtractionService.extract(any()) }
        verify(exactly = 0) { experienceImportService.saveAll(workspaceId = any(), groups = any()) }
    }

    "PDF 텍스트가 비어 있으면 페이지 이미지를 AI로 전사한다" {
        val pdfBytes = samplePdfBytes("")
        val file = MockMultipartFile(
            "file",
            "scanned-resume.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            pdfBytes,
        )
        every { pdfValidationService.validate(file = file, userId = 1L) } returns pdfBytes
        every { promptTemplateRepository.findByType(PromptType.DOCUMENT_TEXT_EXTRACTION) } returns PromptTemplate(
            type = PromptType.DOCUMENT_TEXT_EXTRACTION,
            modelName = "gpt-4o-mini",
            parameters = AiParameters(temperature = 0.0, maxTokens = 4096),
            systemPrompt = "문서를 전사한다",
            jsonSchema = null,
        )
        every { documentVisionClient.extractText(any(), any()) } returns ""

        shouldThrow<InvalidArgumentsException> {
            service.importExperiences(file = file, workspaceId = "workspace-id", userId = 1L)
        }

        verify(exactly = 1) {
            documentVisionClient.extractText(any(), match { pages ->
                pages.size == 1 && pages.single().pageNumber == 1 && pages.single().bytes.isNotEmpty()
            })
        }
        verify(exactly = 0) { experienceAiExtractionService.extract(any()) }
        verify(exactly = 0) { experienceImportService.saveAll(workspaceId = any(), groups = any()) }
    }

})

private fun emptyProfileSections(): ProfileSections {
    return ProfileSections(
        educations = emptyList(),
        careers = emptyList(),
        languageTests = emptyList(),
        awards = emptyList(),
        certifications = emptyList(),
        skills = emptyList(),
    )
}

private fun samplePdfBytes(text: String): ByteArray {
    return PDDocument().use { document ->
        val page = PDPage()
        document.addPage(page)

        PDPageContentStream(document, page).use { contentStream ->
            contentStream.beginText()
            contentStream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            contentStream.newLineAtOffset(50f, 750f)
            contentStream.showText(text)
            contentStream.endText()
        }

        ByteArrayOutputStream().use { outputStream ->
            document.save(outputStream)
            outputStream.toByteArray()
        }
    }
}
