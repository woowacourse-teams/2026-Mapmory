package com.mapmory.shared.data.remote

import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.model.ShareVideoStyle
import com.mapmory.shared.data.settings.PushTokenRegistration
import com.mapmory.shared.data.settings.PushTokenRegistrationManager
import com.mapmory.shared.data.settings.PushTokenRegistrationStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShareVideoRemoteRepositoryTest {
    @Test
    fun `만료된_푸시_등록은_갱신하고_영상_생성을_재시도한다`() = runBlocking {
        val token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ."
        val store = VideoTestPushTokenRegistrationStore()
        var registrationCount = 0
        var createCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/api/v1/push-tokens" -> {
                            registrationCount++
                            respond(
                                content = ByteReadChannel("""{"data":{"pushTokenId":${if (registrationCount == 1) 41 else 42}}}"""),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }

                        "/api/v1/share-videos/travel-timeline" -> {
                            createCount++
                            val requestBody = assertIs<TextContent>(request.body).text
                            val pushTokenId = Json.parseToJsonElement(requestBody).jsonObject["pushTokenId"]
                            if (createCount == 1) {
                                assertEquals("41", pushTokenId?.toString())
                                respond(
                                    content = ByteReadChannel(
                                        """{"title":"푸시 등록을 찾을 수 없습니다.","status":404,"detail":"다시 등록해 주세요.","instance":"/api/v1/share-videos/travel-timeline","code":"SHARE_VIDEO_PUSH_TARGET_NOT_FOUND"}""",
                                    ),
                                    status = HttpStatusCode.NotFound,
                                    headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
                                )
                            } else {
                                assertEquals("42", pushTokenId?.toString())
                                respond(
                                    content = ByteReadChannel("""{"data":{"jobId":15,"status":"PENDING"}}"""),
                                    status = HttpStatusCode.Accepted,
                                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                                )
                            }
                        }

                        else -> error("Unexpected request: ${request.url.encodedPath}")
                    }
                }
            }
        }
        val manager = PushTokenRegistrationManager(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { token },
            store = store,
            platform = "IOS",
        )
        manager.updateDeviceState("device-token", true, photoEnabled = true, videoEnabled = true)

        try {
            val job = ShareVideoRemoteRepository(
                client = client,
                apiBaseUrl = "https://api.example.com/api/v1",
                accessTokenProvider = AccessTokenProvider { token },
                pushTokenRegistrationManager = manager,
            ).createTravelTimeline(ShareVideoStyle.ZOOM).getOrThrow()

            assertEquals(15L, job.jobId)
            assertTrue(job.serverPushRequested)
            assertEquals(2, registrationCount)
            assertEquals(2, createCount)
            assertEquals(PushTokenRegistration("account-1", 42), store.read())
        } finally {
            manager.close()
            client.close()
        }
    }

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
                        assertEquals(ContentType.Application.Json, request.body.contentType)
                        assertEquals(
                            """{"styleId":"ZOOM"}""",
                            assertIs<io.ktor.http.content.TextContent>(request.body).text,
                        )
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

        assertEquals(15L, repository.createTravelTimeline(ShareVideoStyle.ZOOM).getOrThrow().jobId)
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

private class VideoTestPushTokenRegistrationStore : PushTokenRegistrationStore {
    private var registration: PushTokenRegistration? = null
    override fun read() = registration
    override fun write(registration: PushTokenRegistration) { this.registration = registration }
    override fun clear() { registration = null }
}
