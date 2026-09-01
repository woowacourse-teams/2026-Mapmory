package com.mapmory.shared.data.media

import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoadResult
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoader

internal class CachedTripRecordThumbnailLoader(
    private val loader: PhotoPreviewLoader,
) : TripRecordThumbnailLoader {
    override suspend fun load(record: TripRecordSummary): TripRecordThumbnailLoadResult {
        val url = record.thumbnailUrl ?: return TripRecordThumbnailLoadResult.Unavailable
        val result = loader.load(
            objectKey = record.thumbnailCacheKey(url),
            presignedGetUrl = url,
        )
        return when {
            result.isSuccess -> TripRecordThumbnailLoadResult.Success(result.getOrThrow())
            result.exceptionOrNull()?.isExpiredPresignedGetUrl() == true ->
                TripRecordThumbnailLoadResult.UrlExpired
            else -> TripRecordThumbnailLoadResult.Unavailable
        }
    }
}

/**
 * S3 URL의 리소스 경로는 서버가 업로드 때 발급한 Object Key와 같다. 따라서 수정 직후
 * Object Key로 저장한 로컬 미리보기와 목록 썸네일이 같은 캐시 항목을 사용한다.
 */
internal fun TripRecordSummary.thumbnailCacheKey(presignedGetUrl: String): String {
    val path = presignedGetUrl
        .substringAfter(UrlSchemeSeparator, missingDelimiterValue = "")
        .substringAfter('/', missingDelimiterValue = "")
        .substringBefore('?')
        .substringBefore('#')
        .trimStart('/')
    return path.takeIf { it.startsWith(ServerObjectKeyPrefix) }
        ?: "trip-record-thumbnail/$id/${path.ifBlank { presignedGetUrl.substringBefore('?') }}"
}

private const val UrlSchemeSeparator = "://"
private const val ServerObjectKeyPrefix = "travel-records/"
