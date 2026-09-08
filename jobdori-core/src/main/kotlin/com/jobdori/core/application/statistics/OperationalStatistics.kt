package com.jobdori.core.application.statistics

import java.time.LocalDate

data class OperationalStatistics(
    val days: Long = 0,
    val since: LocalDate = LocalDate.MIN,
    val until: LocalDate = LocalDate.MIN,
    val users: Count,
    val withdrawals: Count,
    val jds: Count,
    val profiles: Count,
    val resumes: Count,
    val experiences: Count,
) {

    data class Count(
        val total: Long,
        val recent: Long,
    )

}
