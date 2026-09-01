package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.MapRegionSummaryDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.repository.MapSummaryRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class MapSummaryRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : MapSummaryRepository {
    private val summaryUrl = "${apiBaseUrl.trimEnd('/')}/travel-records/map-summary/regions"

    override suspend fun getRootRegions(): Result<List<MapRegionSummary>> = apiCall {
        client.get("$summaryUrl/roots") {
            authorizeWith(accessTokenProvider)
        }.requireSuccess()
            .body<ApiResponseDto<List<MapRegionSummaryDto>>>()
            .data
            .map(MapRegionSummaryDto::toDomain)
    }

    override suspend fun getChildRegions(regionId: Long): Result<List<MapRegionSummary>> = apiCall {
        require(regionId > 0) { "지역 ID는 양수여야 합니다." }
        client.get("$summaryUrl/$regionId/children") {
            authorizeWith(accessTokenProvider)
        }.requireSuccess()
            .body<ApiResponseDto<List<MapRegionSummaryDto>>>()
            .data
            .map(MapRegionSummaryDto::toDomain)
    }
}
