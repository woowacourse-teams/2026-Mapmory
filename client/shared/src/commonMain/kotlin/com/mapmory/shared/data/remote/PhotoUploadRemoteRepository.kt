package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.PresignedUploadRequestDto
import com.mapmory.shared.data.remote.model.PresignedUploadsDto
import com.mapmory.shared.data.remote.model.UploadFileRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class PhotoUploadSource(
    val localId: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

internal data class UploadedPhoto(
    val source: PhotoUploadSource,
    val objectKey: String,
) {
    val localId: String
        get() = source.localId
}

internal fun interface PhotoUploader {
    suspend fun upload(sources: List<PhotoUploadSource>): Result<List<UploadedPhoto>>
}

internal class PhotoUploadRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : PhotoUploader {
    private val presignedUploadsUrl = "${apiBaseUrl.trimEnd('/')}/uploads/presigned-urls"

    override suspend fun upload(sources: List<PhotoUploadSource>): Result<List<UploadedPhoto>> =
        apiCall {
            if (sources.isEmpty()) return@apiCall emptyList()
            sources.forEach { source ->
                require(source.fileName.isNotBlank()) { "사진 파일 이름은 비어 있을 수 없습니다." }
                require(source.contentType in SupportedImageContentTypes) {
                    "JPEG, PNG, WEBP, HEIC 사진만 업로드할 수 있습니다: ${source.contentType}"
                }
                require(source.bytes.isNotEmpty()) { "빈 사진은 업로드할 수 없습니다." }
            }

            val uploads = client.post(presignedUploadsUrl) {
                authorizeWith(accessTokenProvider)
                contentType(ContentType.Application.Json)
                setBody(
                    PresignedUploadRequestDto(
                        files = sources.map { source ->
                            UploadFileRequestDto(
                                fileName = source.fileName,
                                contentType = source.contentType,
                                fileSize = source.bytes.size.toLong(),
                            )
                        },
                    ),
                )
            }.requireSuccess()
                .body<ApiResponseDto<PresignedUploadsDto>>()
                .data
                .uploads

            require(uploads.size == sources.size) {
                "발급된 업로드 URL 개수가 요청한 사진 개수와 다릅니다."
            }

            coroutineScope {
                sources.zip(uploads).map { (source, upload) ->
                    async {
                        require(upload.method.equals(ExpectedUploadMethod, ignoreCase = true)) {
                            "지원하지 않는 업로드 방식입니다: ${upload.method}"
                        }
                        val contentType = ContentType.parse(upload.contentType)
                        require(contentType.contentType == "image") {
                            "서버가 이미지가 아닌 MIME 타입을 반환했습니다: ${upload.contentType}"
                        }
                        client.put(upload.presignedUrl) {
                            setBody(ByteArrayContent(source.bytes, contentType))
                        }.requireSuccess()
                        UploadedPhoto(source = source, objectKey = upload.objectKey)
                    }
                }.awaitAll()
            }
        }
}

private const val ExpectedUploadMethod = "PUT"
private val SupportedImageContentTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
)
