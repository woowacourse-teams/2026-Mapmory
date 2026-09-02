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
                    "지원하지 않는 사진 형식입니다. JPEG, PNG, WEBP 또는 HEIC 사진을 선택해 주세요."
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
                PhotoUploadPreparationFailureMessage
            }

            coroutineScope {
                sources.zip(uploads).map { (source, upload) ->
                    async {
                        require(upload.method.equals(ExpectedUploadMethod, ignoreCase = true)) {
                            PhotoUploadPreparationFailureMessage
                        }
                        val contentType = runCatching { ContentType.parse(upload.contentType) }
                            .getOrElse { throw IllegalArgumentException(PhotoUploadPreparationFailureMessage) }
                        require(contentType.contentType == "image") {
                            PhotoUploadPreparationFailureMessage
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
private const val PhotoUploadPreparationFailureMessage =
    "사진 업로드를 준비하지 못했습니다. 잠시 후 다시 시도해 주세요."
private val SupportedImageContentTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
)
