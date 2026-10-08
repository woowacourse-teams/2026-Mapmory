package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.PlaceCandidateDto
import com.mapmory.shared.data.remote.model.PlaceSelectionDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.repository.PlaceRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart

class PlaceRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : PlaceRepository {
    private val placesUrl = "${apiBaseUrl.trimEnd('/')}/places"

    override suspend fun searchPlaces(query: String): Result<List<PlaceCandidate>> = apiCall {
        val normalizedQuery = query.trim()
        require(normalizedQuery.length in MinQueryLength..MaxQueryLength) {
            "장소 검색어는 2자 이상 100자 이하로 입력해 주세요."
        }
        client.get("$placesUrl/search") {
            authorizeWith(accessTokenProvider)
            parameter("query", normalizedQuery)
        }.requireSuccess()
            .body<ApiResponseDto<List<PlaceCandidateDto>>>()
            .data
            .take(MaxCandidates)
            .map(PlaceCandidateDto::toDomain)
    }

    override suspend fun selectPlace(placeId: String): Result<PlaceSelection> = apiCall {
        val normalizedPlaceId = placeId.trim()
        require(normalizedPlaceId.isNotEmpty() && normalizedPlaceId.length <= MaxPlaceIdLength) {
            "장소 정보를 확인하지 못했습니다. 다시 검색해 주세요."
        }
        client.get("$placesUrl/${normalizedPlaceId.encodeURLPathPart()}") {
            authorizeWith(accessTokenProvider)
        }.requireSuccess()
            .body<ApiResponseDto<PlaceSelectionDto>>()
            .data
            .toDomain()
    }
}

private const val MinQueryLength = 2
private const val MaxQueryLength = 100
private const val MaxPlaceIdLength = 255
private const val MaxCandidates = 10
