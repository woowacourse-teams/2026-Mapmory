package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.model.PhotoRegionSuggestion

interface PlaceRepository {
    suspend fun searchPlaces(query: String, sessionToken: String): Result<List<PlaceCandidate>>

    suspend fun selectPlace(placeId: String, sessionToken: String): Result<PlaceSelection>

    suspend fun suggestRegion(latitude: Double, longitude: Double): Result<PhotoRegionSuggestion> =
        Result.failure(UnsupportedOperationException("사진 위치 추천을 지원하지 않습니다."))
}
