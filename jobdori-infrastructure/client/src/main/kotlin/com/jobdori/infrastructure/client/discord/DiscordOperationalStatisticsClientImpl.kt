package com.jobdori.infrastructure.client.discord

import com.jobdori.core.application.statistics.OperationalStatistics
import com.jobdori.core.application.statistics.OperationalStatisticsNotificationClient
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.Locale

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
        📊 **Scoop 주요 통계**
        > 기준 일자: $until

        👤 **가입자 (탈퇴 제외)**
        전체 `${users.total.withThousandsSeparator()}명` · 최근 ${days}일 `${users.recent.withThousandsSeparator()}명`

        👋 **탈퇴자**
        전체 `${withdrawals.total.withThousandsSeparator()}명` · 최근 ${days}일 `${withdrawals.recent.withThousandsSeparator()}명`

        🙋 **프로필**
        전체 `${profiles.total.withThousandsSeparator()}개` · 최근 ${days}일 `${profiles.recent.withThousandsSeparator()}개`

        📋 **JD**
        전체 `${jds.total.withThousandsSeparator()}개` · 최근 ${days}일 `${jds.recent.withThousandsSeparator()}개`

        💼 **경험**
        전체 `${experiences.total.withThousandsSeparator()}개` · 최근 ${days}일 `${experiences.recent.withThousandsSeparator()}개`

        📄 **이력서**
        전체 `${resumes.total.withThousandsSeparator()}개` · 최근 ${days}일 `${resumes.recent.withThousandsSeparator()}개`
    """.trimIndent()

    private fun Long.withThousandsSeparator(): String = String.format(Locale.US, "%,d", this)

}
