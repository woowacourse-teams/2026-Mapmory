package com.mapmory.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthRemoteRepositoryTest {
    @Test
    fun serverErrorsRetryWithBackoffAndBlockRequestsUntilDeadline() = runBlocking {
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable)) {
            var calls = 0
            var now = 0L
            var recovered = false
            val waits = mutableListOf<Long>()
            val original = com.mapmory.shared.data.auth.AuthTokens("old-access", "old-refresh")
            var saved = original
            val client = HttpClient(MockEngine) {
                configureCommonHttpClient()
                engine { addHandler { request ->
                    calls++
                    assertEquals("/api/v1/auth/token/refresh", request.url.encodedPath)
                    assertEquals("POST", request.method.value)
                    if (recovered) respondJson("""{"data":{"accessToken":"new-access","refreshToken":"new-refresh"}}""")
                    else respondJson("""{"status":${status.value},"code":"SERVER_ERROR"}""", status)
                } }
            }
            val session = com.mapmory.shared.data.auth.GuestSessionManager(
                AuthRemoteRepository(client, "https://api.example.com/api/v1"),
                object : com.mapmory.shared.data.auth.AuthTokenStore {
                    override fun load() = saved
                    override fun save(tokens: com.mapmory.shared.data.auth.AuthTokens) { saved = tokens }
                    override fun clear() = error("Must preserve guest")
                }, retryDelay = { waits += it }, elapsedMillis = { now },
            )
            try {
                val error = session.ensureAuthenticated().exceptionOrNull() as MapmoryApiException
                assertEquals(status.value, error.statusCode)
                assertEquals(3, calls)
                assertEquals(listOf(1_000L, 2_000L), waits)
                assertEquals(original, saved)
                now = 59_999
                repeat(21) {
                    kotlin.test.assertSame(error, session.ensureAuthenticated().exceptionOrNull())
                    kotlin.test.assertSame(error, session.refreshAfterUnauthorized("old-access").exceptionOrNull())
                }
                assertEquals(3, calls)
                recovered = true
                now = 60_000
                session.ensureAuthenticated().getOrThrow()
                assertEquals(4, calls)
                assertEquals(com.mapmory.shared.data.auth.AuthTokens("new-access", "new-refresh"), saved)
                assertEquals(listOf(1_000L, 2_000L), waits)
            } finally {
                session.close()
                client.close()
            }
        }
    }

    @Test
    fun guestLoginAndRefreshFollowTokenContract() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    requestCount += 1
                    assertNull(request.headers[HttpHeaders.Authorization])
                    when (requestCount) {
                        1 -> {
                            assertEquals("POST", request.method.value)
                            assertEquals("/api/v1/auth/login/guest", request.url.encodedPath)
                            respondJson(
                                """{"data":{"accessToken":"guest-access","refreshToken":"guest-refresh","isNewMember":true}}""",
                            )
                        }

                        else -> {
                            assertEquals("POST", request.method.value)
                            assertEquals("/api/v1/auth/token/refresh", request.url.encodedPath)
                            respondJson(
                                """{"data":{"accessToken":"rotated-access","refreshToken":"rotated-refresh"}}""",
                            )
                        }
                    }
                }
            }
        }
        val repository = AuthRemoteRepository(client, "https://api.example.com/api/v1")

        val guest = repository.loginAsGuest().getOrThrow()
        val rotated = repository.refresh(guest.refreshToken).getOrThrow()

        assertEquals("guest-access", guest.accessToken)
        assertEquals("guest-refresh", guest.refreshToken)
        assertEquals("rotated-access", rotated.accessToken)
        assertEquals("rotated-refresh", rotated.refreshToken)
        assertEquals(2, requestCount)
        client.close()
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = ByteReadChannel(json),
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
