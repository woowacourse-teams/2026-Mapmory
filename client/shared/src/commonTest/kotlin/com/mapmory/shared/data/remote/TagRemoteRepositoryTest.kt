package com.mapmory.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TagRemoteRepositoryTest {
    @Test
    fun `태그를_조회하고_직접_생성한다`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    requestCount += 1
                    assertEquals("Bearer guest-token", request.headers[HttpHeaders.Authorization])
                    assertEquals("/api/v1/tags", request.url.encodedPath)
                    if (requestCount == 1) {
                        assertEquals("GET", request.method.value)
                        jsonResponse("""{"data":[{"id":1,"name":"가족"}]}""")
                    } else {
                        assertEquals("POST", request.method.value)
                        val body = assertIs<TextContent>(request.body).text
                        assertEquals("""{"name":"라멘맛집"}""", body)
                        jsonResponse(
                            """{"data":{"id":2,"name":"라멘맛집","createdAt":"2026-08-31T12:00:00","updatedAt":"2026-08-31T12:00:00"}}""",
                            HttpStatusCode.Created,
                        )
                    }
                }
            }
        }
        val repository = TagRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        assertEquals("가족", repository.getTags().getOrThrow().single().name)
        assertEquals("라멘맛집", repository.createTag("라멘맛집").getOrThrow().name)
        assertEquals(2, requestCount)
        client.close()
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(
        json: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = ByteReadChannel(json),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
