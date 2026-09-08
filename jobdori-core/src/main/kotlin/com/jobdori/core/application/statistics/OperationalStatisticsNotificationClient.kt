package com.jobdori.core.application.statistics

fun interface OperationalStatisticsNotificationClient {

    fun send(statistics: OperationalStatistics)

}
