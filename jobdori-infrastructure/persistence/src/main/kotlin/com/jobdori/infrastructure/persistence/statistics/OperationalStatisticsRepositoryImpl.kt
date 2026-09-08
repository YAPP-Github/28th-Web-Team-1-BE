package com.jobdori.infrastructure.persistence.statistics

import com.jobdori.core.application.statistics.OperationalStatistics
import com.jobdori.core.application.statistics.OperationalStatisticsRepository
import com.jobdori.infrastructure.persistence.domain.experience.repository.ExperienceJpaRepository
import com.jobdori.infrastructure.persistence.domain.jd.repository.JdJpaRepository
import com.jobdori.infrastructure.persistence.domain.profile.repository.ProfileJpaRepository
import com.jobdori.infrastructure.persistence.domain.resume.repository.ResumeJpaRepository
import com.jobdori.infrastructure.persistence.domain.user.repository.UserJpaRepository
import com.jobdori.infrastructure.persistence.domain.user.repository.WithdrawalUserJpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class OperationalStatisticsRepositoryImpl(
    private val userRepository: UserJpaRepository,
    private val withdrawalUserRepository: WithdrawalUserJpaRepository,
    private val jdRepository: JdJpaRepository,
    private val profileRepository: ProfileJpaRepository,
    private val resumeRepository: ResumeJpaRepository,
    private val experienceRepository: ExperienceJpaRepository,
) : OperationalStatisticsRepository {

    override fun get(since: LocalDateTime) = OperationalStatistics(
        users = OperationalStatistics.Count(
            total = userRepository.count(),
            recent = userRepository.countByCreatedAtGreaterThanEqual(since),
        ),
        withdrawals = OperationalStatistics.Count(
            total = withdrawalUserRepository.count(),
            recent = withdrawalUserRepository.countByCreatedAtGreaterThanEqual(since),
        ),
        jds = OperationalStatistics.Count(
            total = jdRepository.count(),
            recent = jdRepository.countByCreatedAtGreaterThanEqual(since),
        ),
        profiles = OperationalStatistics.Count(
            total = profileRepository.count(),
            recent = profileRepository.countByCreatedAtGreaterThanEqual(since),
        ),
        resumes = OperationalStatistics.Count(
            total = resumeRepository.count(),
            recent = resumeRepository.countByCreatedAtGreaterThanEqual(since),
        ),
        experiences = OperationalStatistics.Count(
            total = experienceRepository.count(),
            recent = experienceRepository.countByCreatedAtGreaterThanEqual(since),
        ),
    )

}
