package com.mapmory.shared.analytics

import com.mapmory.shared.data.auth.AuthGateway
import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.data.repository.AuthenticatedPlaceRepository
import com.mapmory.shared.domain.model.PhotoRegionSuggestion
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.repository.PlaceRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhotoRegionRepositoryDelegationTest {
    @Test
    fun `사진_위치_추천은_인증과_분석_래퍼를_통과하고_좌표를_분석에_남기지_않는다`() = runBlocking {
        val suggestion = PhotoRegionSuggestion("KR", PlaceRegionSuggestion("KR", "27", "27110"), false)
        val failure = IllegalStateException("provider unavailable")
        for (result in listOf(Result.success(suggestion), Result.failure(failure))) {
            val tokens = AuthTokens("guest-access", "guest-refresh")
            var guestLogins = 0
            val gateway = object : AuthGateway {
                override suspend fun loginAsGuest(): Result<AuthTokens> {
                    guestLogins++
                    return Result.success(tokens)
                }
                override suspend fun refresh(refreshToken: String) = error("Unexpected refresh")
            }
            var storedTokens: AuthTokens? = null
            val store = object : AuthTokenStore {
                override fun load() = storedTokens
                override fun save(tokens: AuthTokens) { storedTokens = tokens }
                override fun clear() { storedTokens = null }
            }
            val session = GuestSessionManager(gateway, store)
            val analytics = RecordingAnalytics()
            val coordinates = mutableListOf<Pair<Double, Double>>()
            val delegate = object : PlaceRepository {
                override suspend fun searchPlaces(query: String, sessionToken: String): Result<List<PlaceCandidate>> =
                    error("Unexpected place search")
                override suspend fun selectPlace(placeId: String, sessionToken: String): Result<PlaceSelection> =
                    error("Unexpected place selection")
                override suspend fun suggestRegion(latitude: Double, longitude: Double): Result<PhotoRegionSuggestion> {
                    assertEquals(tokens, storedTokens)
                    coordinates += latitude to longitude
                    return result
                }
            }
            val repository = AnalyticsPlaceRepository(AuthenticatedPlaceRepository(session, delegate), analytics)
            try {
                val actual = repository.suggestRegion(35.8694, 128.6062)
                assertEquals(listOf(35.8694 to 128.6062), coordinates)
                assertEquals(1, guestLogins)
                if (result.isSuccess) assertSame(suggestion, actual.getOrThrow())
                else assertSame(failure, actual.exceptionOrNull())
                assertTrue(analytics.events.isEmpty())
            } finally {
                session.close()
            }
        }
    }
}
