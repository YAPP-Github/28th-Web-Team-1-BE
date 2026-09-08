package com.jobdori.infrastructure.client.discord

import com.jobdori.core.application.statistics.OperationalStatistics
import com.jobdori.core.application.statistics.OperationalStatisticsNotificationClient
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class DiscordOperationalStatisticsClientImpl(
    properties: DiscordStatisticsProperties,
) : OperationalStatisticsNotificationClient {

    private val webhookUrl = properties.webhookUrl
    private val restClient = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(2))
            setReadTimeout(Duration.ofSeconds(3))
        })
        .build()

    override fun send(statistics: OperationalStatistics) {
        require(webhookUrl.isNotBlank()) { "DISCORD_STATISTICS_WEBHOOK_URL must be set" }
        restClient.post()
            .uri(webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("content" to statistics.toDiscordContent()))
            .retrieve()
            .toBodilessEntity()
    }

    private fun OperationalStatistics.toDiscordContent() = """
        📊 **Jobdori 주요 통계**
        > 요청 시점 기준 최근 ${days}일

        👤 **현재 가입자**
        전체 `${users.total}명` · 최근 ${days}일 `${users.recent}명`

        👋 **탈퇴**
        전체 `${withdrawals.total}명` · 최근 ${days}일 `${withdrawals.recent}명`

        🙋 **프로필**
        전체 `${profiles.total}개` · 최근 ${days}일 `${profiles.recent}개`

        📋 **JD**
        전체 `${jds.total}개` · 최근 ${days}일 `${jds.recent}개`

        💼 **경험**
        전체 `${experiences.total}개` · 최근 ${days}일 `${experiences.recent}개`

        📄 **이력서**
        전체 `${resumes.total}개` · 최근 ${days}일 `${resumes.recent}개`
    """.trimIndent()

}
