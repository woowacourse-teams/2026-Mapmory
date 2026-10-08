package com.mapmory.shared.domain.usecase

import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository

// 여행 기록 목록 조회를 담당한다.
class GetTripRecordsUseCase(
    private val repository: TripRecordRepository,
) {
    suspend fun cached(query: TripRecordQuery): TripRecordPage? = repository.getCachedTripRecords(query)

    suspend operator fun invoke(query: TripRecordQuery = TripRecordQuery()): Result<TripRecordPage> =
        repository.getTripRecords(query)
}

// 여행 기록 상세 조회를 담당한다.
class GetTripRecordUseCase(
    private val repository: TripRecordRepository,
) {
    suspend operator fun invoke(id: Long): Result<TripRecordData> = repository.getTripRecord(id)
}

// 여행 기록 생성을 담당한다.
class CreateTripRecordUseCase(
    private val repository: TripRecordRepository,
) {
    suspend operator fun invoke(draft: TripRecordDraft): Result<TripRecordData> =
        repository.createTripRecord(draft)
}

// 여행 기록 수정을 담당한다.
class UpdateTripRecordUseCase(
    private val repository: TripRecordRepository,
) {
    suspend operator fun invoke(id: Long, draft: TripRecordDraft): Result<TripRecordData> =
        repository.updateTripRecord(id, draft)
}

// 여행 기록 삭제를 담당한다.
class DeleteTripRecordUseCase(
    private val repository: TripRecordRepository,
) {
    suspend operator fun invoke(id: Long): Result<Unit> = repository.deleteTripRecord(id)
}
