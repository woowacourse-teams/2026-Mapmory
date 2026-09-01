package com.mapmory.shared.data.repository

import com.mapmory.shared.data.remote.PhotoUploadSource
import com.mapmory.shared.data.remote.PhotoUploader
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordMediaDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 새 로컬 사진을 S3에 먼저 올리고 서버 Object Key로 바꾼 뒤 기록 API를 호출한다. */
internal class UploadingTripRecordRepository(
    private val uploader: PhotoUploader,
    private val delegate: TripRecordRepository,
    private val maxCachedPreviewBytes: Long = DefaultMaxCachedPreviewBytes,
) : TripRecordRepository {
    private val mediaCacheMutex = Mutex()
    private val cachedMediaByRecordId = mutableMapOf<Long, List<TripRecordMedia>>()
    private val cachedRecordOrder = mutableListOf<Long>()
    private var cachedPreviewBytes = 0L

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> {
        val page = delegate.getTripRecords(query).getOrElse { error -> return Result.failure(error) }
        val cachedByRecordId = mediaCacheMutex.withLock { cachedMediaByRecordId.toMap() }
        return Result.success(
            page.copy(
                records = page.records.map { record ->
                    cachedByRecordId[record.id]
                        ?.let { media -> record.copy(media = media.toListMedia()) }
                        ?: record
                },
            ),
        )
    }

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> =
        delegate.getTripRecord(id).withCachedMedia()

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        saveWithUploadedMedia(draft, delegate::createTripRecord)

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
        save: suspend (TripRecordDraft) -> Result<TripRecordData>,
    ): Result<TripRecordData> {
        val prepared = prepareDraft(draft).getOrElse { error -> return Result.failure(error) }
        return save(prepared).withCachedMedia(prepared.localMedia)
    }

    private suspend fun prepareDraft(draft: TripRecordDraft): Result<TripRecordDraft> {
        val mediaByLocalId = draft.localMedia.associateBy(TripRecordMediaDraft::objectKey)
        val pendingSources = mutableListOf<PhotoUploadSource>()

        draft.mediaObjectKeys.forEachIndexed { index, key ->
            if (key.isServerObjectKey()) return@forEachIndexed
            val media = mediaByLocalId[key]
                ?: return Result.failure(
                    IllegalStateException("업로드할 사진 데이터를 찾을 수 없습니다: $key"),
                )
            val bytes = media.originalBytes
                ?: return Result.failure(
                    IllegalStateException("사진 원본을 읽지 못했습니다: ${media.fileName ?: key}"),
                )
            val contentType = detectImageContentType(media.fileName, bytes)
                ?: return Result.failure(
                    IllegalArgumentException(
                        "JPEG, PNG, WEBP, HEIC 사진만 업로드할 수 있습니다: ${media.fileName ?: key}",
                    ),
                )
            pendingSources += PhotoUploadSource(
                localId = key,
                fileName = normalizedFileName(media.fileName, contentType, index),
                contentType = contentType,
                bytes = bytes,
            )
        }

        if (pendingSources.isEmpty()) return Result.success(draft)
        val uploads = uploader.upload(pendingSources).getOrElse { error ->
            return Result.failure(error)
        }
        val objectKeyByLocalId = uploads.associate { upload -> upload.localId to upload.objectKey }
        require(objectKeyByLocalId.size == pendingSources.size) {
            "업로드 결과에 누락되거나 중복된 사진이 있습니다."
        }

        return Result.success(
            draft.copy(
                mediaObjectKeys = draft.mediaObjectKeys.map { key ->
                    objectKeyByLocalId[key] ?: key
                },
                localMedia = draft.localMedia.map { media ->
                    objectKeyByLocalId[media.objectKey]
                        ?.let { objectKey -> media.copy(objectKey = objectKey) }
                        ?: media
                },
            ),
        )
    }

    private suspend fun Result<TripRecordData>.withCachedMedia(
        localMedia: List<TripRecordMediaDraft> = emptyList(),
    ): Result<TripRecordData> {
        val record = getOrElse { error -> return Result.failure(error) }
        return mediaCacheMutex.withLock {
            val previousByObjectKey = cachedMediaByRecordId[record.id]
                .orEmpty()
                .associateBy(TripRecordMedia::objectKey)
            val localByObjectKey = localMedia.associateBy(TripRecordMediaDraft::objectKey)
            val enriched = record.copy(
                media = record.media.map { media ->
                    val local = localByObjectKey[media.objectKey]
                    val cached = previousByObjectKey[media.objectKey]
                    media.copy(
                        previewBytes = local?.previewBytes ?: cached?.previewBytes,
                        originalBytes = null,
                        latitude = local?.latitude ?: cached?.latitude,
                        longitude = local?.longitude ?: cached?.longitude,
                        capturedAt = local?.capturedAt ?: cached?.capturedAt,
                    )
                },
            )
            cacheRecordMedia(record.id, enriched.media)
            Result.success(enriched)
        }
    }

    private fun cacheRecordMedia(recordId: Long, media: List<TripRecordMedia>) {
        removeCachedRecord(recordId)
        val compactMedia = media.map { item -> item.copy(originalBytes = null) }
        val previewBytes = compactMedia.sumOf { item -> item.previewBytes?.size?.toLong() ?: 0L }
        if (previewBytes > maxCachedPreviewBytes) return

        cachedMediaByRecordId[recordId] = compactMedia
        cachedRecordOrder += recordId
        cachedPreviewBytes += previewBytes
        while (cachedPreviewBytes > maxCachedPreviewBytes && cachedRecordOrder.isNotEmpty()) {
            removeCachedRecord(cachedRecordOrder.first())
        }
    }

    private fun removeCachedRecord(recordId: Long) {
        val removed = cachedMediaByRecordId.remove(recordId) ?: return
        cachedRecordOrder.remove(recordId)
        cachedPreviewBytes -= removed.sumOf { item ->
            item.previewBytes?.size?.toLong() ?: 0L
        }
    }
}

private fun List<TripRecordMedia>.toListMedia(): List<TripRecordMedia> =
    sortedBy(TripRecordMedia::sortOrder).mapIndexed { index, media ->
        media.copy(
            previewBytes = media.previewBytes.takeIf { index == 0 },
            originalBytes = null,
        )
    }

private fun String.isServerObjectKey(): Boolean = startsWith(ServerObjectKeyPrefix)

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

private const val ServerObjectKeyPrefix = "travel-records/"
private const val DefaultMaxCachedPreviewBytes = 32L * 1024L * 1024L
