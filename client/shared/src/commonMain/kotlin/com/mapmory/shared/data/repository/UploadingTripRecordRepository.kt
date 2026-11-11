package com.mapmory.shared.data.repository

import com.mapmory.shared.data.media.LocalPhotoDataSource
import com.mapmory.shared.data.media.SavedPhotoPreviewWriter
import com.mapmory.shared.data.media.withoutReplacedPreviews
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.remote.PhotoUploadSource
import com.mapmory.shared.data.remote.PhotoUploader
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordMediaDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.ProgressReportingTripRecordRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 새 로컬 사진을 S3에 먼저 올리고 서버 Object Key로 바꾼 뒤 기록 API를 호출한다. */
internal class UploadingTripRecordRepository(
    private val uploader: PhotoUploader,
    private val delegate: TripRecordRepository,
    private val localPhotoDataSource: LocalPhotoDataSource? = null,
    private val savedPreviewWriter: SavedPhotoPreviewWriter? = null,
) : ProgressReportingTripRecordRepository {
    private val mediaCacheMutex = Mutex()

    // Photos whose device preview was rebuilt from an upload in an earlier attempt. A retry skips their
    // completed transfer and has no original, so it must still not hand on their small preview (#686).
    private val previewReplacedLocalIds = mutableSetOf<String>()
    private val cachedRecordById = mutableMapOf<Long, TripRecordData>()
    private val cachedRecordOrder = mutableListOf<Long>()

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> {
        val page = delegate.getTripRecords(query).getOrElse { error -> return Result.failure(error) }
        val cachedByRecordId = mediaCacheMutex.withLock { cachedRecordById.toMap() }
        return Result.success(
            page.copy(
                records = page.records.map { record ->
                    cachedByRecordId[record.id]
                        ?.let { cached -> record.copy(media = cached.media.toListMedia()) }
                        ?: record
                },
            ),
        )
    }

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> {
        mediaCacheMutex.withLock { cachedRecordById[id] }
            ?.let { cached -> return Result.success(cached) }
        return delegate.getTripRecord(id).withCachedMedia()
    }

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        createTripRecord(draft) {}

    override suspend fun createTripRecord(
        draft: TripRecordDraft,
        onProgress: (Int) -> Unit,
    ): Result<TripRecordData> = createTripRecord(draft, onProgress, promptOnAuthFailure = true)

    override suspend fun createTripRecord(
        draft: TripRecordDraft,
        onProgress: (Int) -> Unit,
        promptOnAuthFailure: Boolean,
    ): Result<TripRecordData> = saveWithUploadedMedia(
        draft = draft,
        onProgress = onProgress,
        save = delegate::createTripRecord,
    )

    override suspend fun updateTripRecord(
        id: Long,
        draft: TripRecordDraft,
    ): Result<TripRecordData> = saveWithUploadedMedia(draft) { prepared ->
        delegate.updateTripRecord(id, prepared)
    }

    override suspend fun deleteTripRecord(id: Long): Result<Unit> {
        val result = delegate.deleteTripRecord(id)
        if (result.isSuccess) {
            mediaCacheMutex.withLock {
                removeCachedRecord(id)
            }
        }
        return result
    }

    private suspend fun saveWithUploadedMedia(
        draft: TripRecordDraft,
        onProgress: (Int) -> Unit = {},
        save: suspend (TripRecordDraft) -> Result<TripRecordData>,
    ): Result<TripRecordData> {
        onProgress(0)
        val prepared = prepareDraft(draft, onProgress).getOrElse { error -> return Result.failure(error) }
        onProgress(ServerSaveProgress)
        val firstResult = save(prepared)
        if (firstResult.isSuccess || !draft.canRetryWithFreshObjectKeys(firstResult.exceptionOrNull())) {
            if (firstResult.isSuccess) {
                uploader.forget(draft.mediaObjectKeys)
                forgetReplacedPreviews(draft.mediaObjectKeys)
                draft.mediaObjectKeys.forEach { localPhotoDataSource?.release(it) }
                onProgress(100)
            }
            return firstResult.withCachedMedia(prepared.localMedia)
        }

        uploader.forget(draft.mediaObjectKeys)
        forgetReplacedPreviews(draft.mediaObjectKeys)
        val retried = prepareDraft(draft, onProgress).getOrElse { error -> return Result.failure(error) }
        val result = save(retried).withCachedMedia(retried.localMedia)
        if (result.isSuccess) {
            uploader.forget(draft.mediaObjectKeys)
            forgetReplacedPreviews(draft.mediaObjectKeys)
            draft.mediaObjectKeys.forEach { localPhotoDataSource?.release(it) }
            onProgress(100)
        }
        return result
    }

    private suspend fun prepareDraft(
        draft: TripRecordDraft,
        onProgress: (Int) -> Unit,
    ): Result<TripRecordDraft> {
        val mediaByLocalId = draft.localMedia.associateBy(TripRecordMediaDraft::objectKey)
        val objectKeyByLocalId = mutableMapOf<String, String>()
        val replacedPreviewLocalIds = mutableSetOf<String>()
        val pendingKeys = draft.mediaObjectKeys.filterNot(draft.uploadedMediaObjectKeys::contains)

        if (pendingKeys.isEmpty()) onProgress(ServerSaveProgress)

        pendingKeys.forEachIndexed { index, key ->
            uploader.completedObjectKey(key)?.let { objectKey ->
                objectKeyByLocalId[key] = objectKey
                // Rebuild even if replaced earlier in this session: a background save of the same draft
                // rewrites the small preview under the local key before uploading (#693 review).
                if (rebuildPreviewFromCompletedTransfer(key, objectKey) ||
                    mediaCacheMutex.withLock { key in previewReplacedLocalIds }
                ) {
                    replacedPreviewLocalIds += key
                }
                onProgress(((index + 1) * ServerSaveProgress / pendingKeys.size).coerceAtMost(ServerSaveProgress))
                return@forEachIndexed
            }
            val media = mediaByLocalId[key]
                ?: return Result.failure(
                    IllegalStateException(
                        "사진 정보를 확인하지 못했습니다. 잠시 후 다시 저장해 주세요.",
                    ),
                )
            val bytes = media.originalBytes ?: localPhotoDataSource?.read(key)
                ?: return Result.failure(
                    IllegalStateException(
                        "사진 원본을 불러오지 못했습니다. 잠시 후 다시 저장해 주세요.",
                    ),
                )
            val contentType = detectImageContentType(media.fileName, bytes)
                ?: return Result.failure(
                    IllegalArgumentException(
                        "지원하지 않는 사진 형식입니다. JPEG, PNG, WEBP 또는 HEIC 사진을 선택해 주세요.",
                    ),
                )
            val source = PhotoUploadSource(
                localId = key,
                fileName = normalizedFileName(media.fileName, contentType, index),
                contentType = contentType,
                bytes = bytes,
            )
            val upload = uploadWithRetry(source).getOrElse { error ->
                return Result.failure(error)
            }.singleOrNull()
                ?: return Result.failure(
                    IllegalStateException("업로드 결과에 누락되거나 중복된 사진이 있습니다."),
                )
            if (upload.localId != key || key in objectKeyByLocalId) {
                return Result.failure(
                    IllegalStateException("업로드 결과에 누락되거나 중복된 사진이 있습니다."),
                )
            }
            objectKeyByLocalId[key] = upload.objectKey
            // The original is only in memory now; it is released once the record is saved (#686).
            if (writeSavedPreview(key, upload.objectKey, bytes)) {
                replacedPreviewLocalIds += key
                mediaCacheMutex.withLock { previewReplacedLocalIds += key }
            }
            onProgress(((index + 1) * ServerSaveProgress / pendingKeys.size).coerceAtMost(ServerSaveProgress))
        }

        if (objectKeyByLocalId.isEmpty()) return Result.success(draft)

        return Result.success(
            draft.copy(
                mediaObjectKeys = draft.mediaObjectKeys.map { key ->
                    objectKeyByLocalId[key] ?: key
                },
                uploadedMediaObjectKeys = draft.mediaObjectKeys
                    .map { key -> objectKeyByLocalId[key] ?: key }
                    .toSet(),
                localMedia = draft.localMedia.withoutReplacedPreviews(replacedPreviewLocalIds).map { media ->
                    objectKeyByLocalId[media.objectKey]
                        ?.let { objectKey -> media.copy(objectKey = objectKey) }
                        ?: media
                },
            ),
        )
    }

    private suspend fun forgetReplacedPreviews(localIds: List<String>) {
        if (savedPreviewWriter == null) return
        mediaCacheMutex.withLock { previewReplacedLocalIds -= localIds.toSet() }
    }

    /**
     * A completed transfer has no original bytes (relaunch, or a cancelled save whose OS upload finished),
     * but its staged upload file stays until forget(); rebuild the preview from that file (#693).
     */
    private suspend fun rebuildPreviewFromCompletedTransfer(localId: String, objectKey: String): Boolean {
        val writer = savedPreviewWriter ?: return false
        val rebuilt = try {
            val path = uploader.completedOriginalPath(localId) ?: return false
            writer.writeFromFile(localId, objectKey, path)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            // A better preview is optional; the save must not fail because of it.
            false
        }
        if (rebuilt) mediaCacheMutex.withLock { previewReplacedLocalIds += localId }
        return rebuilt
    }

    private suspend fun writeSavedPreview(localId: String, objectKey: String, original: ByteArray): Boolean {
        val writer = savedPreviewWriter ?: return false
        return try {
            writer.write(localId, objectKey, original)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            // A better preview is optional; the save must not fail because of it.
            false
        }
    }

    private suspend fun uploadWithRetry(source: PhotoUploadSource): Result<List<com.mapmory.shared.data.remote.UploadedPhoto>> {
        var latest = uploader.upload(listOf(source))
        repeat(PhotoUploadRetryCount) { retryIndex ->
            if (latest.isSuccess) return latest
            delay(PhotoUploadRetryDelayMillis * (retryIndex + 1))
            latest = uploader.upload(listOf(source))
        }
        return latest
    }

    private suspend fun Result<TripRecordData>.withCachedMedia(
        localMedia: List<TripRecordMediaDraft> = emptyList(),
    ): Result<TripRecordData> {
        val record = getOrElse { error -> return Result.failure(error) }
        return mediaCacheMutex.withLock {
            val previousByObjectKey = cachedRecordById[record.id]
                ?.media
                .orEmpty()
                .associateBy(TripRecordMedia::objectKey)
            val localByObjectKey = localMedia.associateBy(TripRecordMediaDraft::objectKey)
            val enriched = record.copy(
                media = record.media.map { media ->
                    val local = localByObjectKey[media.objectKey]
                    val cached = previousByObjectKey[media.objectKey]
                    media.copy(
                        previewBytes = local?.previewBytes ?: cached?.previewBytes,
                        previewUri = cached?.previewUri,
                        localPreviewKey = local?.localPreviewKey ?: cached?.localPreviewKey,
                        originalBytes = null,
                        latitude = local?.latitude ?: cached?.latitude,
                        longitude = local?.longitude ?: cached?.longitude,
                        capturedAt = media.capturedAt ?: local?.capturedAt ?: cached?.capturedAt,
                    )
                },
            )
            cacheRecord(enriched)
            Result.success(enriched)
        }
    }

    private fun cacheRecord(record: TripRecordData) {
        removeCachedRecord(record.id)
        cachedRecordById[record.id] = record.copy(
            media = record.media.map { item ->
                item.copy(previewBytes = null, originalBytes = null)
            },
        )
        cachedRecordOrder += record.id
        while (cachedRecordOrder.size > MaxCachedRecords) {
            removeCachedRecord(cachedRecordOrder.first())
        }
    }

    private fun removeCachedRecord(recordId: Long) {
        cachedRecordById.remove(recordId) ?: return
        cachedRecordOrder.remove(recordId)
    }
}

private const val ServerSaveProgress = 95
private const val PhotoUploadRetryCount = 2
private const val PhotoUploadRetryDelayMillis = 400L
private const val MaxCachedRecords = 20

private fun TripRecordDraft.canRetryWithFreshObjectKeys(error: Throwable?): Boolean =
    mediaObjectKeys.any { key -> key !in uploadedMediaObjectKeys } &&
        error is MapmoryApiException &&
        error.code == InvalidObjectKeyCode

private fun List<TripRecordMedia>.toListMedia(): List<TripRecordMedia> =
    sortedBy(TripRecordMedia::sortOrder).map { media ->
        media.copy(
            previewBytes = null,
            originalBytes = null,
        )
    }

private fun normalizedFileName(
    fileName: String?,
    contentType: String,
    index: Int,
): String {
    val simpleName = fileName
        ?.trim()
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.takeIf(String::isNotBlank)
    if (simpleName != null && '.' in simpleName) return simpleName
    return "travel-photo-${index + 1}.${contentType.defaultExtension()}"
}

private fun String.defaultExtension(): String = when (this) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/heic" -> "heic"
    else -> "jpg"
}

internal fun detectImageContentType(fileName: String?, bytes: ByteArray): String? {
    val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    val byExtension = when (extension) {
        "jpg", "jpeg", "jpe" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heics", "heif", "heifs" -> "image/heic"
        else -> null
    }
    if (byExtension != null) return byExtension

    return when {
        bytes.hasPrefix(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        bytes.hasPrefix(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        bytes.hasAsciiAt(0, "RIFF") && bytes.hasAsciiAt(8, "WEBP") -> "image/webp"
        bytes.hasAsciiAt(4, "ftyp") && bytes.hasAnyAsciiAt(
            8,
            "heic",
            "heix",
            "hevc",
            "hevx",
        ) -> "image/heic"
        bytes.hasAsciiAt(4, "ftyp") && bytes.hasAnyAsciiAt(8, "mif1", "msf1") -> "image/heic"
        else -> null
    }
}

private fun ByteArray.hasPrefix(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index ->
        this[index].toInt() and 0xFF == expected[index]
    }

private fun ByteArray.hasAsciiAt(offset: Int, expected: String): Boolean =
    size >= offset + expected.length && expected.indices.all { index ->
        this[offset + index].toInt() and 0xFF == expected[index].code
    }

private fun ByteArray.hasAnyAsciiAt(offset: Int, vararg expected: String): Boolean =
    expected.any { value -> hasAsciiAt(offset, value) }

private const val InvalidObjectKeyCode = "INVALID_OBJECT_KEY"
