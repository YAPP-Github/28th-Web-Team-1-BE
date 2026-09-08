package com.jobdori.core.application.statistics

import java.time.LocalDateTime

fun interface OperationalStatisticsRepository {

    fun get(since: LocalDateTime): OperationalStatistics

}
