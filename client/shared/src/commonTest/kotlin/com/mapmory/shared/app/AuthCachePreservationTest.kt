package com.mapmory.shared.app

import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.repository.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthCachePreservationTest {
    @Test fun tokenStorageReadFailureDoesNotEraseOfflineCaches() {
        var cleared = 0
        val maps = object : MapSummaryCache by MemoryMapSummaryCache() {
            override fun clear() { cleared++ }
        }
        val statistics = object : TripStatisticsCache by MemoryTripStatisticsCache() {
            override fun clear() { cleared++ }
        }
        val client = HttpClient(MockEngine { error("Do not create a new guest on read failure") })
        val container = createGuestRemoteAppContainer(
            client = client,
            apiBaseUrl = "https://example.com/api/v1",
            tokenStore = object : AuthTokenStore {
                override fun load(): AuthTokens? = error("storage locked")
                override fun save(tokens: AuthTokens) = error("Must preserve identity")
                override fun clear() = error("Must not delete credentials")
            },
            mapSummaryCache = maps,
            tripStatisticsCache = statistics,
        )
        try { assertEquals(0, cleared) } finally { container.close() }
    }
}
