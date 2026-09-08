package com.jobdori.infrastructure.persistence.domain.jd.repository

import com.jobdori.infrastructure.persistence.domain.jd.entity.JdEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface JdJpaRepository : JpaRepository<JdEntity, Long> {
    fun findByIdAndWorkspaceId(id: Long, workspaceId: Long): JdEntity?
    fun findAllByIdInAndWorkspaceId(ids: Collection<Long>, workspaceId: Long): List<JdEntity>
    fun findByPublicIdAndWorkspaceId(publicId: String, workspaceId: Long): JdEntity?
    fun findAllByWorkspaceId(workspaceId: Long): List<JdEntity>
    fun countByCreatedAtGreaterThanEqual(since: LocalDateTime): Long
}
