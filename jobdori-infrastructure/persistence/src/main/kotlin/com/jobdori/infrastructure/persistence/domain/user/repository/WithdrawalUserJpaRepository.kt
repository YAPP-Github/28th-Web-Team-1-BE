package com.jobdori.infrastructure.persistence.domain.user.repository

import com.jobdori.infrastructure.persistence.domain.user.entity.WithdrawalUserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface WithdrawalUserJpaRepository : JpaRepository<WithdrawalUserEntity, Long> {
    fun countByCreatedAtGreaterThanEqual(since: LocalDateTime): Long
}
