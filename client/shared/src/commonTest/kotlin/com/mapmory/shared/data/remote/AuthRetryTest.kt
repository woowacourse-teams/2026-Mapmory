package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.AuthGateway
import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.auth.GuestSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRetryTest {
    @Test
    fun unauthorizedApiRequestRefreshesAndRetriesOnlyFailedHttpRequest() = runBlocking {
        val gateway = StubAuthGateway()
        val session = GuestSessionManager(gateway, MemoryTokenStore())
        session.ensureAuthenticated().getOrThrow()
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestCount += 1
                    when (requestCount) {
                        1 -> {
                            assertEquals(
                                "Bearer expired-access",
                                request.headers[HttpHeaders.Authorization],
                            )
                            respond(
                                content = ByteReadChannel(""),
                                status = HttpStatusCode.Unauthorized,
                            )
                        }

                        else -> {
                            assertEquals(
                                "Bearer rotated-access",
                                request.headers[HttpHeaders.Authorization],
                            )
                            respond(
                                content = ByteReadChannel("ok"),
                                status = HttpStatusCode.OK,
                            )
                        }
                    }
                }
            }
        }
        client.installMapmoryAuthRetry(session, ApiBaseUrl)

        val response = client.get("$ApiBaseUrl/travel-records") {
            header(HttpHeaders.Authorization, "Bearer expired-access")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, requestCount)
        assertEquals(1, gateway.refreshCount)
        client.close()
    }

    @Test
    fun unauthorizedExternalRequestDoesNotRotateMapmorySession() = runBlocking {
        val gateway = StubAuthGateway()
        val session = GuestSessionManager(gateway, MemoryTokenStore())
        session.ensureAuthenticated().getOrThrow()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.Unauthorized,
                    )
                }
            }
        }
        client.installMapmoryAuthRetry(session, ApiBaseUrl)

        val response = client.get("https://s3.example.com/photo.jpg") {
            header(HttpHeaders.Authorization, "Bearer expired-access")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, gateway.refreshCount)
        client.close()
    }
}

private class StubAuthGateway : AuthGateway {
    var refreshCount = 0
        private set

    override suspend fun loginAsGuest(): Result<AuthTokens> =
        Result.success(AuthTokens("expired-access", "initial-refresh"))

    override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
        refreshCount += 1
        assertEquals("initial-refresh", refreshToken)
        return Result.success(AuthTokens("rotated-access", "rotated-refresh"))
    }
}

private class MemoryTokenStore : AuthTokenStore {
    private var tokens: AuthTokens? = null

    override fun load(): AuthTokens? = tokens

    override fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}

private const val ApiBaseUrl = "https://api.example.com/api/v1"
