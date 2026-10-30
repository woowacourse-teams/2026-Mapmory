package com.mapmory.shared.data.repository

import com.mapmory.shared.data.media.RecordedPhotoIndex
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.ProgressReportingTripRecordRepository
import kotlinx.coroutines.CancellationException

internal class PhotoUsageTripRecordRepository(
    private val delegate: TripRecordRepository,
    private val index: RecordedPhotoIndex,
) : TripRecordRepository by delegate, ProgressReportingTripRecordRepository {
    override suspend fun createTripRecord(draft: TripRecordDraft) =
        delegate.createTripRecord(draft).remember(draft)

    override suspend fun createTripRecord(draft: TripRecordDraft, onProgress: (Int) -> Unit) =
        ((delegate as? ProgressReportingTripRecordRepository)?.createTripRecord(draft, onProgress)
            ?: delegate.createTripRecord(draft)).remember(draft)

    override suspend fun updateTripRecord(id: Long, draft: TripRecordDraft) =
        delegate.updateTripRecord(id, draft).remember(draft)

    override suspend fun deleteTripRecord(id: Long): Result<Unit> =
        delegate.deleteTripRecord(id).onSuccess { updateIndex(id, emptySet()) }

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> =
        delegate.getTripRecord(id).onSuccess { record ->
            val localIds = record.media.mapNotNull { it.localPreviewKey }.toSet()
            if (localIds.isNotEmpty()) updateIndex(id, localIds)
        }

    private suspend fun Result<TripRecordData>.remember(draft: TripRecordDraft) = onSuccess { record ->
        updateIndex(record.id, draft.localMedia.mapNotNull { it.localPreviewKey }.toSet())
    }

    private suspend fun updateIndex(id: Long, ids: Set<String>) {
        try {
            index.replace(id, ids)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 서버 저장 성공 후 로컬 저장 실패 때문에 기록을 중복 생성하지 않는다.
        }
    }
}
