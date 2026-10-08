package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery

// 여행 기록 데이터를 조회하고 변경하는 도메인 계약
interface TripRecordRepository {
    // 저장된 목록만 읽는다. 네트워크나 인증 갱신을 기다리지 않는다.
    suspend fun getCachedTripRecords(query: TripRecordQuery): TripRecordPage? = null

    // 조건에 맞는 여행 기록 목록을 페이지 단위로 가져온다.
    suspend fun getTripRecords(query: TripRecordQuery = TripRecordQuery()): Result<TripRecordPage>

    // ID로 여행 기록 하나를 가져온다.
    suspend fun getTripRecord(id: Long): Result<TripRecordData>

    // 새 여행 기록을 저장한다.
    suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData>

    // 기존 여행 기록을 수정한다.
    suspend fun updateTripRecord(id: Long, draft: TripRecordDraft): Result<TripRecordData>

    // 여행 기록을 삭제한다.
    suspend fun deleteTripRecord(id: Long): Result<Unit>
}

/** 사진 업로드를 포함한 새 기록 저장 진행률을 전달할 수 있는 Repository 계약이다. */
interface ProgressReportingTripRecordRepository : TripRecordRepository {
    suspend fun createTripRecord(
        draft: TripRecordDraft,
        onProgress: (Int) -> Unit,
    ): Result<TripRecordData>
}
