package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class WithdrawalAuthRetryTest {
    @Test fun expiredWithdrawalDoesNotReenterSessionMutex() = runBlocking {
        for (apple in listOf(false, true)) {
            var withdrawalCalls = 0
            var refreshCalls = 0
            var saved: AuthTokens? = AuthTokens("header.eyJleHAiOjIwMDB9.signature", "refresh")
            var marker: String? = null
            val client = HttpClient(MockEngine) {
                configureCommonHttpClient()
                engine { addHandler { request ->
                    if (request.url.encodedPath.endsWith("/token/refresh")) {
                        refreshCalls++
                        respond("""{"data":{"accessToken":"fresh","refreshToken":"rotated"}}""", HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"))
                    } else {
                        withdrawalCalls++
                        assertEquals(if (apple) "/api/v1/auth/apple/withdrawal" else "/api/v1/members/me", request.url.encodedPath)
                        if (withdrawalCalls == 1) respond("""{"status":401,"code":"EXPIRED_ACCESS_TOKEN"}""", HttpStatusCode.Unauthorized,
                            headersOf(HttpHeaders.ContentType, "application/json"))
                        else {
                            assertEquals("Bearer fresh", request.headers[HttpHeaders.Authorization])
                            respond("", HttpStatusCode.NoContent)
                        }
                    }
                } }
            }
            val session = GuestSessionManager(AuthRemoteRepository(client, "https://api.example.com/api/v1"), object : AuthTokenStore {
                override fun load() = saved
                override fun save(tokens: AuthTokens) { saved = tokens }
                override fun clear() { saved = null }
                override fun pendingProvider() = AuthPendingRead(marker)
                override fun markPendingProvider(provider: String?) { marker = provider }
            }, nowEpochSeconds = { 1_000L })
            client.installMapmoryAuthRetry(session, "https://api.example.com/api/v1")
            try {
                withTimeout(5_000) {
                    session.withdraw(if (apple) "identity-proof" else null, if (apple) "code" else null).getOrThrow()
                }
                assertEquals(2, withdrawalCalls)
                assertEquals(1, refreshCalls)
                assertNull(marker)
                assertNull(saved)
                assertFalse(session.state.value.transitioning)
            } finally { session.close(); client.close() }
        }
    }
}
