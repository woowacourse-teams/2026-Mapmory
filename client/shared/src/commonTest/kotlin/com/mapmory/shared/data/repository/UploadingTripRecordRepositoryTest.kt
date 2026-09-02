package com.mapmory.shared.data.repository

import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.remote.PhotoUploadSource
import com.mapmory.shared.data.remote.PhotoUploader
import com.mapmory.shared.data.remote.UploadedPhoto
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordMediaDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.repository.TripRecordRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UploadingTripRecordRepositoryTest {
    @Test
    fun updateKeepsServerMediaAndUploadsOnlyNewLocalPhotosInOrder() = runBlocking {
        val existingObjectKey = "mapmory/travel-records/10/existing.jpg"
        val newObjectKey = "travel-records/10/new.jpg"
        val newBytes = byteArrayOf(0x01, 0x02)
        var uploadedSources: List<PhotoUploadSource> = emptyList()
        val uploader = PhotoUploader { sources ->
            uploadedSources = sources
            Result.success(sources.map { source -> UploadedPhoto(source, newObjectKey) })
        }
        val delegate = CapturingTripRecordRepository()
        val repository = UploadingTripRecordRepository(uploader, delegate)

        val result = repository.updateTripRecord(
            id = 101,
            draft = TripRecordDraft(
                locationId = 1,
                title = "수정한 기록",
                content = "",
                startDate = "2026-08-26",
                endDate = null,
                mediaObjectKeys = listOf(existingObjectKey, "content://photo/new"),
                uploadedMediaObjectKeys = setOf(existingObjectKey),
                localMedia = listOf(
                    TripRecordMediaDraft(
                        objectKey = existingObjectKey,
                        sortOrder = 0,
                        previewBytes = null,
                    ),
                    TripRecordMediaDraft(
                        objectKey = "content://photo/new",
                        sortOrder = 1,
                        previewBytes = byteArrayOf(0x0A),
                        originalBytes = newBytes,
                        fileName = "new-photo.jpg",
                    ),
                ),
            ),
        ).getOrThrow()

        assertEquals(listOf("content://photo/new"), uploadedSources.map(PhotoUploadSource::localId))
        assertEquals("image/jpeg", uploadedSources.single().contentType)
        assertContentEquals(newBytes, uploadedSources.single().bytes)
        assertEquals(
            listOf(existingObjectKey, newObjectKey),
            delegate.updatedDraft?.mediaObjectKeys,
        )
        assertContentEquals(byteArrayOf(0x0A), result.media[1].previewBytes)
        assertNull(result.media[1].originalBytes)
    }

    @Test
    fun existingServerMediaIsRecognizedWithoutInterpretingItsObjectKey() = runBlocking {
        val opaqueObjectKey = "production-prefix/member-media/existing.jpg"
        var uploadCalled = false
        val uploader = PhotoUploader {
            uploadCalled = true
            Result.success(emptyList())
        }
        val delegate = CapturingTripRecordRepository()
        val repository = UploadingTripRecordRepository(uploader, delegate)

        repository.updateTripRecord(
            id = 101,
            draft = TripRecordDraft(
                locationId = 1,
                title = "기존 사진 기록",
                content = "",
                startDate = "2026-08-26",
                endDate = null,
                mediaObjectKeys = listOf(opaqueObjectKey),
                uploadedMediaObjectKeys = setOf(opaqueObjectKey),
                localMedia = listOf(
                    TripRecordMediaDraft(
                        objectKey = opaqueObjectKey,
                        sortOrder = 0,
                        previewBytes = null,
                    ),
                ),
            ),
        ).getOrThrow()

        assertFalse(uploadCalled)
        assertEquals(listOf(opaqueObjectKey), delegate.updatedDraft?.mediaObjectKeys)
    }

    @Test
    fun missingLocalOriginalDoesNotExposeItsInternalIdentifier() = runBlocking {
        val internalId = "content://media/external/images/media/12345"
        val repository = UploadingTripRecordRepository(
            uploader = indexedUploader(),
            delegate = CapturingTripRecordRepository(),
        )

        val error = repository.updateTripRecord(
            id = 101,
            draft = TripRecordDraft(
                locationId = 1,
                title = "원본 없는 기록",
                content = "",
                startDate = "2026-08-26",
                endDate = null,
                mediaObjectKeys = listOf(internalId),
                localMedia = listOf(
                    TripRecordMediaDraft(
                        objectKey = internalId,
                        sortOrder = 0,
                        previewBytes = byteArrayOf(0x01),
                        originalBytes = null,
                        fileName = "internal-file-name.jpg",
                    ),
                ),
            ),
        ).exceptionOrNull()

        assertEquals(
            "사진 원본을 불러오지 못했습니다. 잠시 후 다시 저장해 주세요.",
            error?.message,
        )
        assertFalse(error?.message.orEmpty().contains(internalId))
        assertFalse(error?.message.orEmpty().contains("internal-file-name.jpg"))
    }

    @Test
    fun invalidNewObjectKeyIsReissuedAndSavedOnceWithoutReselectingThePhoto() = runBlocking {
        var uploadAttempt = 0
        val uploader = PhotoUploader { sources ->
            uploadAttempt += 1
            Result.success(
                sources.map { source ->
                    UploadedPhoto(source, "travel-records/10/reissued-$uploadAttempt.jpg")
                },
            )
        }
        val savedDrafts = mutableListOf<TripRecordDraft>()
        val baseDelegate = CapturingTripRecordRepository()
        val delegate = object : TripRecordRepository by baseDelegate {
            override suspend fun updateTripRecord(
                id: Long,
                draft: TripRecordDraft,
            ): Result<TripRecordData> {
                savedDrafts += draft
                return if (savedDrafts.size == 1) {
                    Result.failure(invalidObjectKeyError())
                } else {
                    Result.success(draft.toRecord(id))
                }
            }
        }
        val repository = UploadingTripRecordRepository(uploader, delegate)

        val result = repository.updateTripRecord(
            id = 101,
            draft = draftWithPhotos("다시 선택하지 않는 기록", listOf(byteArrayOf(0x01, 0x02))),
        ).getOrThrow()

        assertEquals(2, uploadAttempt)
        assertEquals(2, savedDrafts.size)
        assertEquals("travel-records/10/reissued-1.jpg", savedDrafts[0].mediaObjectKeys.single())
        assertEquals("travel-records/10/reissued-2.jpg", savedDrafts[1].mediaObjectKeys.single())
        assertEquals("travel-records/10/reissued-2.jpg", result.media.single().objectKey)
    }

    @Test
    fun listCacheKeepsOnlyTheFirstPreviewAndNeverKeepsOriginalBytes() = runBlocking {
        val uploader = indexedUploader()
        val delegate = CapturingTripRecordRepository()
        val repository = UploadingTripRecordRepository(uploader, delegate)

        repository.updateTripRecord(
            id = 101,
            draft = draftWithPhotos(
                title = "사진 두 장",
                photoBytes = listOf(
                    byteArrayOf(0x01, 0x02),
                    byteArrayOf(0x03, 0x04),
                ),
            ),
        ).getOrThrow()

        val media = repository.getTripRecords(TripRecordQuery()).getOrThrow()
            .records.single().media

        assertEquals(2, media.size)
        assertContentEquals(byteArrayOf(0x01, 0x02), media[0].previewBytes)
        assertNull(media[1].previewBytes)
        media.forEach { assertNull(it.originalBytes) }
    }

    @Test
    fun previewCacheEvictsOldRecordsWhenItReachesTheByteLimit() = runBlocking {
        val delegate = CapturingTripRecordRepository()
        val repository = UploadingTripRecordRepository(
            uploader = indexedUploader(),
            delegate = delegate,
            maxCachedPreviewBytes = 3,
        )

        repository.updateTripRecord(
            id = 101,
            draft = draftWithPhotos("첫 기록", listOf(byteArrayOf(0x01, 0x02))),
        ).getOrThrow()
        repository.updateTripRecord(
            id = 102,
            draft = draftWithPhotos("둘째 기록", listOf(byteArrayOf(0x03, 0x04))),
        ).getOrThrow()

        val records = repository.getTripRecords(TripRecordQuery()).getOrThrow().records

        assertEquals(emptyList(), records.first { it.id == 101L }.media)
        assertContentEquals(
            byteArrayOf(0x03, 0x04),
            records.first { it.id == 102L }.media.single().previewBytes,
        )
    }
}

private class CapturingTripRecordRepository : TripRecordRepository {
    private val records = linkedMapOf<Long, TripRecordData>()

    var updatedDraft: TripRecordDraft? = null
        private set

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> =
        Result.success(
            TripRecordPage(
                records = records.values.map { record ->
                    TripRecordSummary(
                        id = record.id,
                        title = record.title,
                        regionName = "여행지",
                        startDate = record.startDate,
                        endDate = record.endDate,
                    )
                },
                page = 0,
                size = 20,
                totalElements = records.size.toLong(),
                totalPages = if (records.isEmpty()) 0 else 1,
            ),
        )

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> =
        records[id]?.let(Result.Companion::success)
            ?: Result.failure(NoSuchElementException())

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        Result.failure(UnsupportedOperationException())

    override suspend fun updateTripRecord(
        id: Long,
        draft: TripRecordDraft,
    ): Result<TripRecordData> {
        updatedDraft = draft
        return Result.success(draft.toRecord(id).also { record -> records[id] = record })
    }

    override suspend fun deleteTripRecord(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}

private fun indexedUploader(): PhotoUploader = PhotoUploader { sources ->
    Result.success(
        sources.mapIndexed { index, source ->
            UploadedPhoto(source, "travel-records/10/uploaded-$index.jpg")
        },
    )
}

private fun invalidObjectKeyError(): MapmoryApiException = MapmoryApiException(
    statusCode = 400,
    code = "INVALID_OBJECT_KEY",
    title = "Object Key가 올바르지 않습니다.",
    detail = "중복되거나 다른 여행 일지에서 사용 중인 Object Key가 포함되어 있습니다.",
    instance = "/api/v1/travel-records/101",
    errors = emptyList(),
)

private fun draftWithPhotos(
    title: String,
    photoBytes: List<ByteArray>,
): TripRecordDraft = TripRecordDraft(
    locationId = 1,
    title = title,
    content = "",
    startDate = "2026-08-26",
    endDate = null,
    mediaObjectKeys = photoBytes.indices.map { index -> "content://photo/$index" },
    localMedia = photoBytes.mapIndexed { index, bytes ->
        TripRecordMediaDraft(
            objectKey = "content://photo/$index",
            sortOrder = index,
            previewBytes = bytes,
            originalBytes = bytes,
            fileName = "photo-$index.jpg",
        )
    },
)

private fun TripRecordDraft.toRecord(id: Long): TripRecordData = TripRecordData(
    id = id,
    locationId = locationId,
    title = title,
    content = content,
    startDate = requireNotNull(startDate),
    endDate = endDate,
    media = mediaObjectKeys.mapIndexed { index, objectKey ->
        TripRecordMedia(
            id = index.toLong(),
            objectKey = objectKey,
            sortOrder = index,
            url = null,
        )
    },
    createdAt = "2026-08-26T00:00:00",
    updatedAt = "2026-08-26T00:00:00",
)
