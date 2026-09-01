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
