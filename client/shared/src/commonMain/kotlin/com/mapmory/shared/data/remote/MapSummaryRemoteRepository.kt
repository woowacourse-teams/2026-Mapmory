package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.MapRegionSummaryDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.repository.MapSummaryRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class MapSummaryRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : MapSummaryRepository {
    private val summaryUrl = "${apiBaseUrl.trimEnd('/')}/travel-records/map-summary/regions"

    override suspend fun getRootRegions(tagId: Long?): Result<List<MapRegionSummary>> = apiCall {
        require(tagId == null || tagId > 0) { "선택한 태그 정보를 확인하지 못했습니다." }
        client.get("$summaryUrl/roots") {
            authorizeWith(accessTokenProvider)
            tagId?.let { parameter("tagId", it) }
        }.requireSuccess()
            .body<ApiResponseDto<List<MapRegionSummaryDto>>>()
            .data
            .map(MapRegionSummaryDto::toDomain)
    }

    override suspend fun getChildRegions(regionId: Long, tagId: Long?): Result<List<MapRegionSummary>> = apiCall {
        require(regionId > 0) { "선택한 지역 정보를 확인하지 못했습니다." }
        require(tagId == null || tagId > 0) { "선택한 태그 정보를 확인하지 못했습니다." }
        client.get("$summaryUrl/$regionId/children") {
            authorizeWith(accessTokenProvider)
            tagId?.let { parameter("tagId", it) }
        }.requireSuccess()
            .body<ApiResponseDto<List<MapRegionSummaryDto>>>()
            .data
            .map(MapRegionSummaryDto::toDomain)
    }
}
