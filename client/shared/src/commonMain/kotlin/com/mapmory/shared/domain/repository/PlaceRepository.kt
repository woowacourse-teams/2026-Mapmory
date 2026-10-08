package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceSelection

interface PlaceRepository {
    suspend fun searchPlaces(query: String, sessionToken: String): Result<List<PlaceCandidate>>

    suspend fun selectPlace(placeId: String, sessionToken: String): Result<PlaceSelection>
}
