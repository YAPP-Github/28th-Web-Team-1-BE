package com.jobdori.infrastructure.client.discord

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "discord.statistics")
data class DiscordStatisticsProperties(
    val webhookUrl: String = "",
)
