package com.jobdori.api.application.statistics

import com.jobdori.core.application.statistics.SendOperationalStatisticsService
import com.jobdori.core.domain.auth.error.InvalidAuthTokenException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

@RestController
class StatisticsController(
    private val service: SendOperationalStatisticsService,
    private val properties: StatisticsApiProperties,
) {

    @PostMapping("/v1/internal/statistics")
    fun sendStatistics(
        @RequestHeader("X-Jobdori-Admin-Token", required = false) token: String?,
        @RequestParam days: Long,
    ) {
        if (token.isNullOrBlank() || !MessageDigest.isEqual(token.toByteArray(), properties.token.toByteArray())) {
            throw InvalidAuthTokenException("유효하지 않은 관리자 토큰입니다.")
        }
        service.send(days)
    }

}
