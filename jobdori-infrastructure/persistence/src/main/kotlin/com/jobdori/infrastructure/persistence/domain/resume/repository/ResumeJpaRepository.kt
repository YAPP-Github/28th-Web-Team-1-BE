package com.jobdori.infrastructure.persistence.domain.resume.repository

import com.jobdori.core.domain.resume.ResumeStatus
import com.jobdori.infrastructure.persistence.domain.resume.entity.ResumeEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface ResumeJpaRepository : JpaRepository<ResumeEntity, Long>, ResumeCustomRepository {

    fun countByCreatedAtGreaterThanEqual(since: LocalDateTime): Long

    fun findByIdAndWorkspaceIdAndStatusIn(
        id: Long,
        workspaceId: Long,
        statuses: Collection<ResumeStatus>,
    ): ResumeEntity?

    fun existsByIdAndWorkspaceIdAndStatusIn(
        id: Long,
        workspaceId: Long,
        statuses: Collection<ResumeStatus>,
    ): Boolean

    fun findAllByWorkspaceIdAndStatusInOrderByIdDesc(
        workspaceId: Long,
        statuses: Collection<ResumeStatus>,
        pageable: Pageable,
    ): List<ResumeEntity>

    fun findAllByWorkspaceIdAndStatusInAndIdLessThanOrderByIdDesc(
        workspaceId: Long,
        statuses: Collection<ResumeStatus>,
        id: Long,
        pageable: Pageable,
    ): List<ResumeEntity>

}
