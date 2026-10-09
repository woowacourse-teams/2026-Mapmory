package com.mapmory.shared.data.repository

import com.mapmory.shared.data.media.PhotoPreviewLoader
import com.mapmory.shared.data.media.LocalPhotoDataSource
import kotlinx.coroutines.CancellationException
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.ProgressReportingTripRecordRepository

/**
 * 여행 기록 규칙과 무관한 사진 조회·캐시만 덧붙이는 Repository 데코레이터다.
 * 기존 원격/업로드 Repository는 수정하지 않고 AppContainer에서 조합한다.
 */
internal class CachedMediaTripRecordRepository(
    private val delegate: TripRecordRepository,
    private val loader: PhotoPreviewLoader,
    private val localPhotoDataSource: LocalPhotoDataSource? = null,
) : ProgressReportingTripRecordRepository {
    // 목록 데이터는 사진 다운로드를 기다리지 않고 즉시 반환한다.
    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> =
        delegate.getTripRecords(query).map { page ->
            page.copy(
                records = page.records.map { record ->
                    record.copy(
                        media = record.media.sortedBy(TripRecordMedia::sortOrder).mapIndexed { index, media ->
                            if (index == 0) media.withCachedPreviewUri() else media.withoutInMemoryPhoto()
                        },
                    )
                },
            )
        }

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> {
        val record = delegate.getTripRecord(id).getOrElse { error -> return Result.failure(error) }
        // 사진 다운로드는 화면에 보이는 항목만 이미지 로더가 처리한다.
        return Result.success(record.copy(
            media = record.media.sortedBy(TripRecordMedia::sortOrder).map { it.withCachedPreviewUri() },
        ))
    }

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        delegate.createTripRecord(draft).map { record -> record.cacheAvailablePreviews() }

    override suspend fun createTripRecord(
        draft: TripRecordDraft,
        onProgress: (Int) -> Unit,
    ): Result<TripRecordData> {
        val reporting = delegate as? ProgressReportingTripRecordRepository
        return (reporting?.createTripRecord(draft, onProgress) ?: delegate.createTripRecord(draft))
            .map { record -> record.cacheAvailablePreviews() }
    }

    override suspend fun updateTripRecord(
        id: Long,
        draft: TripRecordDraft,
    ): Result<TripRecordData> =
        delegate.updateTripRecord(id, draft).map { record -> record.cacheAvailablePreviews() }

    override suspend fun deleteTripRecord(id: Long): Result<Unit> = delegate.deleteTripRecord(id)

    private suspend fun TripRecordData.cacheAvailablePreviews(): TripRecordData = copy(
        media = media.map { item ->
            item.localPreviewKey?.let { key ->
                loader.rememberLocalSource(item.objectKey, key)
                loader.copyCached(key, item.objectKey)
            }
            item.previewBytes?.let { bytes -> loader.loadFromLocal(item.objectKey, bytes) }
            item.withCachedPreviewUri()
        },
    )

    private suspend fun TripRecordMedia.withCachedPreviewUri(): TripRecordMedia {
        val resolvedLocalPreviewKey = localPreviewKey ?: loader.localSourceKey(objectKey)
        if (loader.cachedUri(objectKey) == null) {
            resolvedLocalPreviewKey?.let { key -> loader.copyCached(key, objectKey) }
        }
        val cachedDate = loader.capturedAt(objectKey)
        val resolvedDate = capturedAt?.takeIf(String::isNotBlank)
            ?: cachedDate
            ?: resolvedLocalPreviewKey?.let { key ->
                try {
                    localPhotoDataSource?.capturedAt(key)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
        if (resolvedDate != null && resolvedDate != cachedDate) {
            loader.rememberCapturedAt(objectKey, resolvedDate)
        }
        val resolvedMedia = copy(localPreviewKey = resolvedLocalPreviewKey, capturedAt = resolvedDate)
        val preview = loader.cachedForDisplay(objectKey) ?: return resolvedMedia.withoutInMemoryPhoto()
        return resolvedMedia.copy(
            previewUri = preview.uri,
            previewBytes = preview.bytes,
            originalBytes = null,
        )
    }
}

private fun TripRecordMedia.withoutInMemoryPhoto(): TripRecordMedia =
    copy(previewBytes = null, originalBytes = null)

private suspend fun PhotoPreviewLoader.loadFromLocal(objectKey: String, bytes: ByteArray) {
    // 로컬에서 선택한 미리보기도 같은 Object Key로 저장해 다음 앱 실행에서 재사용한다.
    store(objectKey, bytes)
}
