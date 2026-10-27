package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.AuthGateway
import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.auth.GuestSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRetryTest {
    @Test
    fun nonExpiredOrUnreadableUnauthorizedResponseIsReturnedUnchanged() = runBlocking {
        val bodies = listOf(
            """{"status":401,"code":"INVALID_ACCESS_TOKEN"}""",
            """{"status":401,"code":"INVALID_REFRESH_TOKEN"}""",
            """{"status":401,"code":"UNKNOWN_AUTH_ERROR"}""",
            """{"status":401}""",
            """{"status":401,"code":null}""",
            """{"code":"EXPIRED_ACCESS_TOKEN""",
            "<html>Unauthorized</html>",
            "",
        )
        for (body in bodies) {
            val gateway = StubAuthGateway()
            val session = GuestSessionManager(gateway, MemoryTokenStore())
            session.ensureAuthenticated().getOrThrow()
            var requestCount = 0
            val client = HttpClient(MockEngine) {
                engine { addHandler {
                    requestCount++
                    respond(content = ByteReadChannel(body), status = HttpStatusCode.Unauthorized,
                        headers = io.ktor.http.headersOf("X-Auth-Test", "original"))
                } }
            }
            try {
                client.installMapmoryAuthRetry(session, ApiBaseUrl)
                val response = client.get("$ApiBaseUrl/travel-records") {
                    header(HttpHeaders.Authorization, "Bearer expired-access")
                }
                assertEquals(HttpStatusCode.Unauthorized, response.status, body)
                assertEquals("original", response.headers["X-Auth-Test"], body)
                assertEquals(body, response.bodyAsText(), body)
                assertEquals(0, gateway.refreshCount, body)
                assertEquals(1, requestCount, body)
            } finally {
                session.close()
                client.close()
            }
        }
    }

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
                                content = ByteReadChannel(
                                    """{"status":401,"code":"EXPIRED_ACCESS_TOKEN"}""",
                                ),
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

    @Test
    fun unauthorizedApiRequestWithoutExpiredAccessTokenCodeIsNotRefreshedOrRetried() = runBlocking {
        val errorBodies = listOf(
            """{"status":401,"code":"INVALID_REFRESH_TOKEN"}""",
            """{"status":401}""",
            "not-json",
        )

        errorBodies.forEach { errorBody ->
            val gateway = StubAuthGateway()
            val session = GuestSessionManager(gateway, MemoryTokenStore())
            session.ensureAuthenticated().getOrThrow()
            var requestCount = 0
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler {
                        requestCount++
                        respond(
                            content = ByteReadChannel(errorBody),
                            status = HttpStatusCode.Unauthorized,
                        )
                    }
                }
            }
            client.installMapmoryAuthRetry(session, ApiBaseUrl)

            val response = client.get("$ApiBaseUrl/travel-records") {
                header(HttpHeaders.Authorization, "Bearer expired-access")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(errorBody, response.bodyAsText())
            assertEquals(1, requestCount)
            assertEquals(0, gateway.refreshCount)
            client.close()
            session.close()
        }
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
