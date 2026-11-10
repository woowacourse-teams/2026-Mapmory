package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.PlaceCandidateDto
import com.mapmory.shared.data.remote.model.PlaceSelectionDto
import com.mapmory.shared.data.remote.model.PhotoLocationRequestDto
import com.mapmory.shared.data.remote.model.PhotoRegionSuggestionDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceRules
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.model.PhotoRegionSuggestion
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.repository.PlaceRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

class PlaceRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : PlaceRepository {
    private val placesUrl = "${apiBaseUrl.trimEnd('/')}/places"

    override suspend fun suggestRegion(latitude: Double, longitude: Double): Result<PhotoRegionSuggestion> = apiCall {
        require(latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0) { "올바른 사진 위치가 아닙니다." }
        val response = client.post("$placesUrl/location") {
            authorizeWith(accessTokenProvider)
            contentType(ContentType.Application.Json)
            setBody(PhotoLocationRequestDto(latitude, longitude))
        }.requireSuccess().body<ApiResponseDto<PhotoRegionSuggestionDto>>().data
        PhotoRegionSuggestion(
            countryCode = response.countryCode,
            suggestedRegion = response.suggestedRegion?.let { region ->
                PlaceRegionSuggestion(region.country.code, region.province?.code, region.district?.code)
            },
            manualRegionRequired = response.manualRegionRequired,
            attribution = response.attribution,
            attributionUrl = response.attributionUrl,
        )
    }

    override suspend fun searchPlaces(query: String, sessionToken: String): Result<List<PlaceCandidate>> = apiCall {
        val normalizedQuery = query.trim()
        require(normalizedQuery.length in PlaceRules.MinQueryLength..PlaceRules.MaxQueryLength) {
            "장소 검색어는 1자 이상 100자 이하로 입력해 주세요."
        }
        client.get("$placesUrl/search") {
            authorizeWith(accessTokenProvider)
            parameter("query", normalizedQuery)
            parameter("sessionToken", sessionToken)
        }.requireSuccess()
            .body<ApiResponseDto<List<PlaceCandidateDto>>>()
            .data
            .take(PlaceRules.MaxCandidates)
            .map(PlaceCandidateDto::toDomain)
    }

    override suspend fun selectPlace(placeId: String, sessionToken: String): Result<PlaceSelection> = apiCall {
        val normalizedPlaceId = placeId.trim()
        require(normalizedPlaceId.isNotEmpty() && normalizedPlaceId.length <= PlaceRules.MaxPlaceIdLength) {
            "장소 정보를 확인하지 못했습니다. 다시 검색해 주세요."
        }
        client.get("$placesUrl/${normalizedPlaceId.encodeURLPathPart()}") {
            authorizeWith(accessTokenProvider)
            parameter("sessionToken", sessionToken)
        }.requireSuccess()
            .body<ApiResponseDto<PlaceSelectionDto>>()
            .data
            .toDomain()
    }
}
