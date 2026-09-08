package com.jobdori.core.application.statistics

import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SendOperationalStatisticsService(
    private val repository: OperationalStatisticsRepository,
    private val notificationClient: OperationalStatisticsNotificationClient,
) {

    fun send(days: Long, now: LocalDateTime = LocalDateTime.now()): OperationalStatistics {
        require(days > 0) { "통계 조회 일수는 0보다 커야 합니다." }

        val statistics = repository.get(now.minusDays(days)).copy(days = days)
        notificationClient.send(statistics)
        return statistics
    }

}
