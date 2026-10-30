package com.mapmory.shared.data.remote

import com.mapmory.shared.domain.model.ServiceFeedbackCategory
import com.mapmory.shared.domain.model.ServiceFeedbackPlatform
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

class ServiceFeedbackRemoteRepositoryTest {
    @Test
    fun `의견을_인증과_함께_분류값을_전송한다`() = runBlocking {
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    assertEquals("Bearer guest-token", request.headers[HttpHeaders.Authorization])
                    assertEquals("POST", request.method.value)
                    assertEquals("/api/v1/service-feedback", request.url.encodedPath)
                    assertEquals(
                        """{"category":"USABILITY","content":"지도 글씨가 작아요","platform":"ANDROID"}""",
                        assertIs<TextContent>(request.body).text,
                    )
                    respond(
                        content = ByteReadChannel("""{"data":{"feedbackId":1,"createdAt":"2026-10-13T00:00:00"}}"""),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        val repository = ServiceFeedbackRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        repository.submit(
            ServiceFeedbackCategory.USABILITY,
            "  지도 글씨가 작아요  ",
            ServiceFeedbackPlatform.ANDROID,
        ).getOrThrow()
        client.close()
    }
}
