package com.jobdori.infrastructure.persistence.domain.user.repository

import com.jobdori.infrastructure.persistence.domain.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface UserJpaRepository : JpaRepository<UserEntity, Long> {

    fun findByPublicId(publicId: String): UserEntity?

    fun countByCreatedAtGreaterThanEqual(since: LocalDateTime): Long

}
