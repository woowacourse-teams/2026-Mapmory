package com.mapmory.shared.data.repository

import com.mapmory.shared.data.media.MemoryPhotoPreviewCache
import com.mapmory.shared.data.media.PhotoPreviewCache
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
import kotlin.test.assertNull

class CachedMediaTripRecordRepositoryTest {
    @Test
    fun `재시작_후_원본_연결로_촬영일을_복원하고_다음_조회는_영속_날짜를_쓴다`() = runBlocking {
        val cache = MemoryPhotoPreviewCache()
        cache.linkLocalSource(ObjectKey, "content://photos/42")
        var metadataReads = 0
        val local = object : com.mapmory.shared.data.media.LocalPhotoDataSource {
            override suspend fun read(localId: String): ByteArray? = error("원본 다운로드 금지")
            override suspend fun capturedAt(localId: String): String? {
                assertEquals("content://photos/42", localId)
                metadataReads++
                return "2026.09.11"
            }
        }
        fun repository(source: com.mapmory.shared.data.media.LocalPhotoDataSource?) = CachedMediaTripRecordRepository(
            delegate = RefreshingDetailRepository(),
            loader = PhotoPreviewLoader(cache, PhotoRemoteSource { error("네트워크 다운로드 금지") }),
            localPhotoDataSource = source,
        )
        assertEquals("2026.09.11", repository(local).getTripRecord(101).getOrThrow().media.single().capturedAt)
        assertEquals("2026.09.11", repository(null).getTripRecord(101).getOrThrow().media.single().capturedAt)
        assertEquals(1, metadataReads)
    }

    @Test
    fun `원본에_접근할_수_없으면_촬영일을_기록_시작일로_채우지_않는다`() = runBlocking {
        val cache = MemoryPhotoPreviewCache()
        cache.linkLocalSource(ObjectKey, "content://photos/deleted")
        val local = object : com.mapmory.shared.data.media.LocalPhotoDataSource {
            override suspend fun read(localId: String): ByteArray? = null
            override suspend fun capturedAt(localId: String): String? = error("접근 불가")
        }
        val repository = CachedMediaTripRecordRepository(
            RefreshingDetailRepository(),
            PhotoPreviewLoader(cache, PhotoRemoteSource { error("다운로드 금지") }),
            local,
        )
        assertNull(repository.getTripRecord(101).getOrThrow().media.single().capturedAt)
    }

    @Test
    fun `저장한_촬영일은_새_저장소에서도_복원한다`() = runBlocking {
        val cache = MemoryPhotoPreviewCache()
        val source = RefreshingDetailRepository()
        val saved = source.getTripRecord(101).getOrThrow().let { record ->
            record.copy(media = record.media.map { it.copy(capturedAt = "2026.09.11") })
        }
        val saving = object : TripRecordRepository by source {
            override suspend fun createTripRecord(draft: TripRecordDraft) = Result.success(saved)
        }
        fun loader() = PhotoPreviewLoader(cache, PhotoRemoteSource { error("다운로드 금지") })
        CachedMediaTripRecordRepository(saving, loader()).createTripRecord(
            TripRecordDraft(1, "2026-09-09", listOf(ObjectKey)),
        ).getOrThrow()
        val restored = CachedMediaTripRecordRepository(RefreshingDetailRepository(), loader())
            .getTripRecord(101).getOrThrow()
        assertEquals("2026.09.11", restored.media.single().capturedAt)
    }

    @Test
    fun diskCacheUriIsReturnedWithoutKeepingPreviewBytesInTheRecord() = runBlocking {
        val cache = UriPhotoPreviewCache()
        cache.write(ObjectKey, byteArrayOf(0x01, 0x02))
        var downloadCount = 0
        val repository = CachedMediaTripRecordRepository(
            delegate = RefreshingDetailRepository(),
            loader = PhotoPreviewLoader(
                cache = cache,
                remoteSource = PhotoRemoteSource {
                    downloadCount += 1
                    Result.success(byteArrayOf(0x03))
                },
            ),
        )

        val media = repository.getTripRecord(101).getOrThrow().media.single()

        assertEquals("file:///cache/$ObjectKey", media.previewUri)
        assertNull(media.previewBytes)
        assertEquals(0, downloadCount)
    }

    @Test
    fun detailDoesNotWaitForPhotoDownloads() = runBlocking {
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

        assertEquals(1, delegate.detailRequestCount)
        assertEquals(emptyList(), requestedUrls)
        assertNull(record.media.single().previewBytes)
        assertEquals(ExpiredUrl, record.media.single().url)
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

    @Test
    fun localPhotoIdentifierIsRestoredWithoutKeepingOriginalPhotoBytes() = runBlocking {
        val cache = MemoryPhotoPreviewCache()
        cache.write(ObjectKey, byteArrayOf(0x0A))
        cache.linkLocalSource(ObjectKey, "content://photos/selected-42")
        val repository = CachedMediaTripRecordRepository(
            delegate = RefreshingDetailRepository(),
            loader = PhotoPreviewLoader(
                cache = cache,
                remoteSource = PhotoRemoteSource { Result.failure(AssertionError("network")) },
            ),
        )

        val media = repository.getTripRecord(101).getOrThrow().media.single()

        assertEquals("content://photos/selected-42", media.localPreviewKey)
        assertContentEquals(byteArrayOf(0x0A), media.previewBytes)
        assertNull(media.originalBytes)
    }
}

private class UriPhotoPreviewCache : PhotoPreviewCache {
    private val values = mutableMapOf<String, ByteArray>()

    override suspend fun read(objectKey: String): ByteArray? = values[objectKey]

    override suspend fun write(objectKey: String, bytes: ByteArray) {
        values[objectKey] = bytes.copyOf()
    }

    override suspend fun uri(objectKey: String): String? =
        objectKey.takeIf(values::containsKey)?.let { "file:///cache/$it" }
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
