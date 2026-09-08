package com.jobdori.core.application.statistics

import java.time.LocalDate

fun interface OperationalStatisticsRepository {

    fun get(since: LocalDate): OperationalStatistics

}
