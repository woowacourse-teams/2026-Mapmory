package com.mapmory.shared.data.media

import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoadResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CachedTripRecordThumbnailLoaderTest {
    @Test
    fun `수정_직후_Object_Key로_저장한_미리보기를_목록에서도_재사용한다`() = runBlocking {
        val cache = MemoryPhotoPreviewCache()
        cache.write(ObjectKey, byteArrayOf(0x01, 0x02))
        var downloadCount = 0
        val loader = thumbnailLoader(cache) {
            downloadCount += 1
            Result.success(byteArrayOf(0x03))
        }

        val result = assertIs<TripRecordThumbnailLoadResult.Success>(
            loader.load(summary(FirstSignedUrl)),
        )

        assertEquals(0, downloadCount)
        assertContentEquals(byteArrayOf(0x01, 0x02), result.previewBytes)
    }

    @Test
    fun `Presigned_서명이_바뀌어도_같은_Object_Key_캐시를_사용한다`() = runBlocking {
        var downloadCount = 0
        val loader = thumbnailLoader(MemoryPhotoPreviewCache()) {
            downloadCount += 1
            Result.success(byteArrayOf(0x11))
        }

        val first = assertIs<TripRecordThumbnailLoadResult.Success>(
            loader.load(summary(FirstSignedUrl)),
        )
        val second = assertIs<TripRecordThumbnailLoadResult.Success>(
            loader.load(summary(RotatedSignedUrl)),
        )

        assertEquals(1, downloadCount)
        assertContentEquals(first.previewBytes, second.previewBytes)
    }

    @Test
    fun `S3_403은_URL_갱신이_필요한_결과로_변환한다`() = runBlocking {
        val loader = thumbnailLoader(MemoryPhotoPreviewCache()) {
            Result.failure(forbiddenError())
        }

        assertIs<TripRecordThumbnailLoadResult.UrlExpired>(
            loader.load(summary(FirstSignedUrl)),
        )
        Unit
    }

    private fun thumbnailLoader(
        cache: PhotoPreviewCache,
        download: suspend (String) -> Result<ByteArray>,
    ) = CachedTripRecordThumbnailLoader(
        PhotoPreviewLoader(
            cache = cache,
            remoteSource = PhotoRemoteSource { url -> download(url) },
        ),
    )

    private fun summary(url: String) = TripRecordSummary(
        id = 101,
        title = "제주 여행",
        regionName = "제주시",
        startDate = "2026-08-27",
        endDate = null,
        thumbnailUrl = url,
    )
}

private fun forbiddenError() = MapmoryApiException(
    statusCode = 403,
    code = "HTTP_403",
    title = null,
    detail = null,
    instance = null,
    errors = emptyList(),
)

private const val ObjectKey = "travel-records/10/photo.jpg"
private const val FirstSignedUrl =
    "https://bucket.example.com/travel-records/10/photo.jpg?signature=first"
private const val RotatedSignedUrl =
    "https://bucket.example.com/travel-records/10/photo.jpg?signature=rotated"
