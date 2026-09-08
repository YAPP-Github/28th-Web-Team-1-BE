package com.jobdori.infrastructure.persistence.domain.experience.repository

import com.jobdori.core.domain.experience.ExperienceStatus
import com.jobdori.infrastructure.persistence.domain.experience.entity.ExperienceEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ExperienceJpaRepository : JpaRepository<ExperienceEntity, Long>, ExperienceCustomRepository {

    fun countByCreatedAtGreaterThanEqual(since: LocalDateTime): Long

    fun findByIdAndWorkspaceIdAndStatus(
        id: Long,
        workspaceId: Long,
        status: ExperienceStatus,
    ): ExperienceEntity?

    fun findAllByWorkspaceIdAndStatus(
        workspaceId: Long,
        status: ExperienceStatus,
    ): List<ExperienceEntity>

    // 경험 세트 시그니처(개수 + 최신 수정시각). 추가/수정/삭제 감지용.
    @Query(
        """
        select count(e.id) as cnt, max(e.updatedAt) as maxUpdatedAt
        from ExperienceEntity e
        where e.workspaceId = :workspaceId and e.status = :status
        """,
    )
    fun signatureView(
        @Param("workspaceId") workspaceId: Long,
        @Param("status") status: ExperienceStatus,
    ): ExperienceSignatureView

    fun findAllByWorkspaceIdAndStatusOrderByIdDesc(
        workspaceId: Long,
        status: ExperienceStatus,
        pageable: Pageable,
    ): List<ExperienceEntity>

    fun findAllByWorkspaceIdAndStatusAndIdLessThanOrderByIdDesc(
        workspaceId: Long,
        status: ExperienceStatus,
        id: Long,
        pageable: Pageable,
    ): List<ExperienceEntity>

    fun findAllByWorkspaceIdAndProjectIdAndStatusOrderByIdDesc(
        workspaceId: Long,
        projectId: Long,
        status: ExperienceStatus,
        pageable: Pageable,
    ): List<ExperienceEntity>

    fun findAllByWorkspaceIdAndProjectIdAndStatusAndIdLessThanOrderByIdDesc(
        workspaceId: Long,
        projectId: Long,
        status: ExperienceStatus,
        id: Long,
        pageable: Pageable,
    ): List<ExperienceEntity>

}

interface ExperienceSignatureView {
    val cnt: Long
    val maxUpdatedAt: LocalDateTime?
}
