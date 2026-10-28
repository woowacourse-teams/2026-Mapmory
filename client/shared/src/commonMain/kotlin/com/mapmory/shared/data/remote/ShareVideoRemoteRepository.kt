package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.CreateShareVideoRequestDto
import com.mapmory.shared.data.remote.model.ShareVideoDownloadDto
import com.mapmory.shared.data.remote.model.ShareVideoJobDto
import com.mapmory.shared.domain.model.ShareVideoDownload
import com.mapmory.shared.domain.model.ShareVideoJob
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.model.ShareVideoStyle
import com.mapmory.shared.domain.repository.ShareVideoRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ShareVideoRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : ShareVideoRepository {
    private val shareVideosUrl = "${apiBaseUrl.trimEnd('/')}/share-videos"

    override suspend fun createTravelTimeline(style: ShareVideoStyle): Result<ShareVideoJob> = apiCall {
        client.post("$shareVideosUrl/travel-timeline") {
            authorizeWith(accessTokenProvider)
            contentType(ContentType.Application.Json)
            setBody(CreateShareVideoRequestDto(style.name))
        }.requireSuccess()
            .body<ApiResponseDto<ShareVideoJobDto>>()
            .data
            .toDomain()
    }

    override suspend fun getJob(jobId: Long): Result<ShareVideoJob> = apiCall {
        requireValidJobId(jobId)
        client.get("$shareVideosUrl/$jobId") {
            authorizeWith(accessTokenProvider)
        }.requireSuccess()
            .body<ApiResponseDto<ShareVideoJobDto>>()
            .data
            .toDomain()
    }

    override suspend fun getDownloadUrl(jobId: Long): Result<ShareVideoDownload> = apiCall {
        requireValidJobId(jobId)
        val download = client.get("$shareVideosUrl/$jobId/download-url") {
            authorizeWith(accessTokenProvider)
        }.requireSuccess()
            .body<ApiResponseDto<ShareVideoDownloadDto>>()
            .data
        require(download.jobId == jobId && download.downloadUrlExpiresIn > 0) {
            "영상 다운로드 정보를 확인하지 못했어요. 다시 시도해 주세요."
        }
        require(download.downloadUrl.startsWith("https://", ignoreCase = true)) {
            "안전한 영상 다운로드 주소를 확인하지 못했어요."
        }
        ShareVideoDownload(download.jobId, download.downloadUrl, download.downloadUrlExpiresIn)
    }

    private fun requireValidJobId(jobId: Long) {
        require(jobId > 0) { "영상 작업을 찾을 수 없습니다." }
    }
}

private fun ShareVideoJobDto.toDomain() = ShareVideoJob(
    jobId = jobId.also { require(it > 0) { "영상 작업 정보를 확인하지 못했어요." } },
    status = ShareVideoStatus.valueOf(status),
)
