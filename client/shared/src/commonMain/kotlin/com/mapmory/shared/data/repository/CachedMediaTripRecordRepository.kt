package com.mapmory.shared.data.repository

import com.mapmory.shared.data.media.PhotoPreviewLoader
import com.mapmory.shared.data.media.isExpiredPresignedGetUrl
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository

/**
 * 여행 기록 규칙과 무관한 사진 조회·캐시만 덧붙이는 Repository 데코레이터다.
 * 기존 원격/업로드 Repository는 수정하지 않고 AppContainer에서 조합한다.
 */
internal class CachedMediaTripRecordRepository(
    private val delegate: TripRecordRepository,
    private val loader: PhotoPreviewLoader,
) : TripRecordRepository {
    // 목록 데이터는 사진 다운로드를 기다리지 않고 즉시 반환한다.
    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> =
        delegate.getTripRecords(query)

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> {
        val record = delegate.getTripRecord(id).getOrElse { error -> return Result.failure(error) }
        val firstLoad = record.loadPreviews()
        if (!firstLoad.hasExpiredUrl) return Result.success(firstLoad.record)

        // 상세 재조회가 새 Presigned GET URL을 발급하는 서버 계약을 사용한다.
        val refreshed = delegate.getTripRecord(id).getOrNull() ?: return Result.success(firstLoad.record)
        return Result.success(refreshed.loadPreviews().record)
    }

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        delegate.createTripRecord(draft).map { record -> record.cacheAvailablePreviews() }

    override suspend fun updateTripRecord(
        id: Long,
        draft: TripRecordDraft,
    ): Result<TripRecordData> =
        delegate.updateTripRecord(id, draft).map { record -> record.cacheAvailablePreviews() }

    override suspend fun deleteTripRecord(id: Long): Result<Unit> = delegate.deleteTripRecord(id)

    private suspend fun TripRecordData.loadPreviews(): PreviewLoad {
        var hasExpiredUrl = false
        val loaded = media.sortedBy(TripRecordMedia::sortOrder).map { item ->
            val result = item.withLoadedPreview()
            if (result.hasExpiredUrl) hasExpiredUrl = true
            result.media
        }
        return PreviewLoad(copy(media = loaded), hasExpiredUrl)
    }

    private suspend fun TripRecordData.cacheAvailablePreviews(): TripRecordData = copy(
        media = media.map { item ->
            item.previewBytes?.let { bytes -> loader.loadFromLocal(item.objectKey, bytes) }
            item
        },
    )

    private suspend fun TripRecordMedia.withLoadedPreview(): MediaLoad {
        previewBytes?.let { bytes ->
            loader.loadFromLocal(objectKey, bytes)
            return MediaLoad(this, hasExpiredUrl = false)
        }
        val getUrl = url ?: return MediaLoad(this, hasExpiredUrl = false)
        val result = loader.load(objectKey, getUrl)
        return MediaLoad(
            media = result.getOrNull()?.let { bytes -> copy(previewBytes = bytes) } ?: this,
            hasExpiredUrl = result.exceptionOrNull()?.isExpiredPresignedGetUrl() == true,
        )
    }
}

private suspend fun PhotoPreviewLoader.loadFromLocal(objectKey: String, bytes: ByteArray) {
    // 로컬에서 선택한 미리보기도 같은 Object Key로 저장해 다음 앱 실행에서 재사용한다.
    store(objectKey, bytes)
}

private data class PreviewLoad(
    val record: TripRecordData,
    val hasExpiredUrl: Boolean,
)

private data class MediaLoad(
    val media: TripRecordMedia,
    val hasExpiredUrl: Boolean,
)
