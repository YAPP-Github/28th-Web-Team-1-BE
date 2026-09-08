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

        val until = now.toLocalDate()
        val since = until.minusDays(days)
        val statistics = repository.get(since).copy(days = days, since = since, until = until)
        notificationClient.send(statistics)
        return statistics
    }

}
