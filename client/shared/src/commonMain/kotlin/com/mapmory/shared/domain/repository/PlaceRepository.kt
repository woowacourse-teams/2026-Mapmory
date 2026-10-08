package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceSelection

interface PlaceRepository {
    suspend fun searchPlaces(query: String): Result<List<PlaceCandidate>>

    suspend fun selectPlace(placeId: String): Result<PlaceSelection>
}
