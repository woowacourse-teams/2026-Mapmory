package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.TripStatisticsDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.domain.repository.TripStatisticsRepository
import com.mapmory.shared.logging.mapmoryDebugLog
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class TripStatisticsRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : TripStatisticsRepository {
    private val statisticsUrl = "${apiBaseUrl.trimEnd('/')}/travel-records/statistics"

    override suspend fun getStatistics(): Result<TripStatistics> {
        mapmoryDebugLog(StatisticsLogTag, "request GET /travel-records/statistics")
        return apiCall {
            val response = client.get(statisticsUrl) {
                authorizeWith(accessTokenProvider)
            }.requireSuccess()
                .body<ApiResponseDto<TripStatisticsDto>>()
                .data
            mapmoryDebugLog(StatisticsLogTag, "request succeeded")
            response.toDomain()
        }.onFailure { error ->
            mapmoryDebugLog(
                StatisticsLogTag,
                "request failed: ${error::class.simpleName}",
            )
        }
    }
}

private const val StatisticsLogTag = "MapmoryStatistics"
