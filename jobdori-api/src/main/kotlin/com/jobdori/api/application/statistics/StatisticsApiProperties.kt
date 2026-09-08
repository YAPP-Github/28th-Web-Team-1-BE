package com.jobdori.api.application.statistics

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jobdori.admin")
data class StatisticsApiProperties(
    val token: String = "",
)
