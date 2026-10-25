package com.mapmory.shared.data.remote

import com.mapmory.shared.domain.model.ShareVideoStatus
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
import kotlin.test.assertIs

class ShareVideoRemoteRepositoryTest {
    @Test
    fun `여행 타임라인 생성 상태와 다운로드 주소를 계약대로 조회한다`() = runBlocking {
        val responses = listOf(
            Triple(HttpStatusCode.Accepted, """{"data":{"jobId":15,"status":"PENDING"}}""", "/api/v1/share-videos/travel-timeline"),
            Triple(HttpStatusCode.OK, """{"data":{"jobId":15,"status":"RENDERING"}}""", "/api/v1/share-videos/15"),
            Triple(HttpStatusCode.OK, """{"data":{"jobId":15,"status":"COMPLETED"}}""", "/api/v1/share-videos/15"),
            Triple(HttpStatusCode.OK, """{"data":{"jobId":15,"downloadUrl":"https://cdn.example.com/video.mp4?sig=private","downloadUrlExpiresIn":900}}""", "/api/v1/share-videos/15/download-url"),
        ).toMutableList()
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    assertEquals("Bearer guest-token", request.headers[HttpHeaders.Authorization])
                    val expected = responses.removeAt(0)
                    assertEquals(expected.third, request.url.encodedPath)
                    if (expected.third.endsWith("travel-timeline")) {
                        assertEquals("POST", request.method.value)
                        assertEquals(0L, request.body.contentLength)
                    } else {
                        assertEquals("GET", request.method.value)
                    }
                    respond(
                        content = ByteReadChannel(expected.second),
                        status = expected.first,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        val repository = ShareVideoRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        assertEquals(15L, repository.createTravelTimeline().getOrThrow().jobId)
        assertEquals(ShareVideoStatus.RENDERING, repository.getJob(15).getOrThrow().status)
        assertEquals(ShareVideoStatus.COMPLETED, repository.getJob(15).getOrThrow().status)
        val download = repository.getDownloadUrl(15).getOrThrow()
        assertEquals("https://cdn.example.com/video.mp4?sig=private", download.downloadUrl)
        assertEquals(900L, download.downloadUrlExpiresIn)
        assertEquals(emptyList(), responses)
        client.close()
    }

    @Test
    fun `영상 미완료 응답의 문제 코드를 보존한다`() = runBlocking {
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel(
                            """{"type":"about:blank","title":"영상이 아직 준비되지 않았습니다.","status":409,"detail":"다시 요청해 주세요.","instance":"/api/v1/share-videos/15/download-url","code":"SHARE_VIDEO_NOT_READY"}""",
                        ),
                        status = HttpStatusCode.Conflict,
                        headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
                    )
                }
            }
        }
        val repository = ShareVideoRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        val error = assertIs<MapmoryApiException>(repository.getDownloadUrl(15).exceptionOrNull())
        assertEquals("SHARE_VIDEO_NOT_READY", error.code)
        assertEquals(409, error.statusCode)
        client.close()
    }
}
