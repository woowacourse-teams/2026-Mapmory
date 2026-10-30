package com.mapmory.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs

class PhotoUploadRemoteRepositoryTest {
    @Test
    fun recordLimitPhotosAreUploadedInPresignedBatches() = runBlocking {
        val sources = (1..100).map { index ->
            PhotoUploadSource(
                localId = "content://photo/$index",
                fileName = "photo-$index.jpg",
                contentType = "image/jpeg",
                bytes = byteArrayOf(index.toByte()),
            )
        }
        val expectedBatchSizes = List(10) { 10 }
        var batchIndex = 0
        var objectIndex = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    when (request.method.value) {
                        "POST" -> {
                            val batchSize = expectedBatchSizes.getOrNull(batchIndex++)
                                ?: error("예상보다 많은 업로드 URL 발급 요청입니다.")
                            val uploads = List(batchSize) {
                                objectIndex += 1
                                """{"objectKey":"travel-records/10/photo-$objectIndex.jpg","presignedUrl":"https://bucket.example.com/$objectIndex.jpg?signature=test","method":"PUT","contentType":"image/jpeg","expiresIn":300}"""
                            }.joinToString(",")
                            respondJson("""{"data":{"uploads":[$uploads]}}""")
                        }

                        "PUT" -> {
                            respond(
                                content = ByteReadChannel(""),
                                status = HttpStatusCode.OK,
                            )
                        }

                        else -> error("예상하지 못한 요청입니다: ${request.method.value}")
                    }
                }
            }
        }
        val repository = PhotoUploadRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-access-token" },
        )

        val uploads = repository.upload(sources).getOrThrow()

        assertEquals(expectedBatchSizes.size, batchIndex)
        assertEquals(100, uploads.size)
        assertEquals(sources.map(PhotoUploadSource::localId), uploads.map(UploadedPhoto::localId))
        client.close()
    }

    @Test
    fun presignedUrlIsRequestedWithBearerAndBinaryIsPutDirectlyToS3() = runBlocking {
        val originalBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    requestCount += 1
                    when (requestCount) {
                        1 -> {
                            assertEquals("POST", request.method.value)
                            assertEquals("/api/v1/uploads/presigned-urls", request.url.encodedPath)
                            assertEquals(
                                "Bearer guest-access-token",
                                request.headers[HttpHeaders.Authorization],
                            )
                            assertEquals(ContentType.Application.Json, request.body.contentType)
                            respondJson(
                                """{"data":{"uploads":[{"objectKey":"travel-records/10/server-photo.jpg","presignedUrl":"https://bucket.example.com/server-photo.jpg?signature=test","method":"PUT","contentType":"image/jpeg","expiresIn":300}]}}""",
                            )
                        }

                        else -> {
                            assertEquals("PUT", request.method.value)
                            assertEquals("bucket.example.com", request.url.host)
                            assertEquals("/server-photo.jpg", request.url.encodedPath)
                            assertNull(request.headers[HttpHeaders.Authorization])
                            assertEquals(ContentType.Image.JPEG, request.body.contentType)
                            assertEquals(originalBytes.size.toLong(), request.body.contentLength)
                            val body = assertIs<ByteArrayContent>(request.body)
                            assertContentEquals(originalBytes, body.bytes())
                            respond(
                                content = ByteReadChannel(""),
                                status = HttpStatusCode.OK,
                            )
                        }
                    }
                }
            }
        }
        val repository = PhotoUploadRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-access-token" },
        )

        val uploads = repository.upload(
            listOf(
                PhotoUploadSource(
                    localId = "content://photo/1",
                    fileName = "seoul-trip.jpg",
                    contentType = "image/jpeg",
                    bytes = originalBytes,
                ),
            ),
        ).getOrThrow()

        assertEquals(1, uploads.size)
        assertEquals("content://photo/1", uploads.single().localId)
        assertEquals("travel-records/10/server-photo.jpg", uploads.single().objectKey)
        assertContentEquals(originalBytes, uploads.single().source.bytes)
        assertEquals(2, requestCount)
        client.close()
    }

    @Test
    fun uploadProblemDetailsAreReturnedWithoutCallingS3() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler {
                    requestCount += 1
                    respond(
                        content = ByteReadChannel(
                            """{"title":"허용되지 않은 파일 형식입니다.","status":400,"detail":"jpeg, png, webp, heic 형식의 이미지만 업로드할 수 있습니다.","instance":"/api/v1/uploads/presigned-urls","code":"INVALID_FILE_TYPE","errors":[]}""",
                        ),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            "application/problem+json",
                        ),
                    )
                }
            }
        }
        val repository = PhotoUploadRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-access-token" },
        )

        val error = repository.upload(
            listOf(
                PhotoUploadSource(
                    localId = "content://photo/1",
                    fileName = "photo.jpg",
                    contentType = "image/jpeg",
                    bytes = byteArrayOf(0x01),
                ),
            ),
        ).exceptionOrNull()

        val apiError = assertIs<MapmoryApiException>(error)
        assertEquals(400, apiError.statusCode)
        assertEquals("INVALID_FILE_TYPE", apiError.code)
        assertEquals(1, requestCount)
        client.close()
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    json: String,
) = respond(
    content = ByteReadChannel(json),
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
