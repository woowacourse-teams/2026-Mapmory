package com.mapmory.shared.data.repository

import com.mapmory.shared.data.media.MemoryPhotoPreviewCache
import com.mapmory.shared.data.media.PhotoPreviewLoader
import com.mapmory.shared.data.media.PhotoRemoteSource
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CachedMediaTripRecordRepositoryTest {
    @Test
    fun expiredGetUrlRefreshesDetailAndDownloadsWithNewUrlOnce() = runBlocking {
        val delegate = RefreshingDetailRepository()
        val requestedUrls = mutableListOf<String>()
        val repository = CachedMediaTripRecordRepository(
            delegate = delegate,
            loader = PhotoPreviewLoader(
                cache = MemoryPhotoPreviewCache(),
                remoteSource = PhotoRemoteSource { url ->
                    requestedUrls += url
                    if ("expired" in url) Result.failure(forbiddenError())
                    else Result.success(byteArrayOf(0x01, 0x02))
                },
            ),
        )

        val record = repository.getTripRecord(101).getOrThrow()

        assertEquals(2, delegate.detailRequestCount)
        assertEquals(listOf(ExpiredUrl, RefreshedUrl), requestedUrls)
        assertContentEquals(byteArrayOf(0x01, 0x02), record.media.single().previewBytes)
        assertEquals(RefreshedUrl, record.media.single().url)
    }

    @Test
    fun cachedObjectKeySkipsNetworkEvenWhenGetUrlChanges() = runBlocking {
        val cache = MemoryPhotoPreviewCache()
        cache.write(ObjectKey, byteArrayOf(0x0A))
        var downloadCount = 0
        val repository = CachedMediaTripRecordRepository(
            delegate = RefreshingDetailRepository(),
            loader = PhotoPreviewLoader(
                cache = cache,
                remoteSource = PhotoRemoteSource {
                    downloadCount += 1
                    Result.success(byteArrayOf(0x01))
                },
            ),
        )

        val record = repository.getTripRecord(101).getOrThrow()

        assertEquals(0, downloadCount)
        assertContentEquals(byteArrayOf(0x0A), record.media.single().previewBytes)
    }
}

private class RefreshingDetailRepository : TripRecordRepository {
    var detailRequestCount = 0
        private set

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> {
        detailRequestCount += 1
        return Result.success(record(if (detailRequestCount == 1) ExpiredUrl else RefreshedUrl))
    }

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> =
        Result.failure(UnsupportedOperationException())

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        Result.failure(UnsupportedOperationException())

    override suspend fun updateTripRecord(
        id: Long,
        draft: TripRecordDraft,
    ): Result<TripRecordData> = Result.failure(UnsupportedOperationException())

    override suspend fun deleteTripRecord(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    private fun record(url: String) = TripRecordData(
        id = 101,
        locationId = 1,
        title = "제주 여행",
        content = "",
        startDate = "2026-08-27",
        endDate = null,
        media = listOf(
            TripRecordMedia(
                id = 1,
                objectKey = ObjectKey,
                sortOrder = 0,
                url = url,
            ),
        ),
        createdAt = "2026-08-27T00:00:00",
        updatedAt = "2026-08-27T00:00:00",
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
private const val ExpiredUrl =
    "https://bucket.example.com/travel-records/10/photo.jpg?expired=true"
private const val RefreshedUrl =
    "https://bucket.example.com/travel-records/10/photo.jpg?signature=rotated"
