package com.mapmory.shared.data.repository

import com.mapmory.shared.data.media.MemoryPhotoPreviewCache
import com.mapmory.shared.data.media.RecordedPhotoIndex
import com.mapmory.shared.domain.model.*
import com.mapmory.shared.domain.repository.TripRecordRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoUsageTripRecordRepositoryTest {
    @Test
    fun `서버가_성공한_생성_수정_삭제만_사용_이력에_반영한다`() = runBlocking {
        var fail = false
        val server = object : TripRecordRepository {
            fun result(draft: TripRecordDraft): Result<TripRecordData> = if (fail) {
                Result.failure(IllegalStateException("offline"))
            } else {
                Result.success(TripRecordData(1, 1, "", "", draft.startDate, null, emptyList(), "", ""))
            }
            override suspend fun createTripRecord(draft: TripRecordDraft) = result(draft)
            override suspend fun updateTripRecord(id: Long, draft: TripRecordDraft) = result(draft)
            override suspend fun deleteTripRecord(id: Long): Result<Unit> =
                if (fail) Result.failure(IllegalStateException("offline")) else Result.success(Unit)
            override suspend fun getTripRecord(id: Long): Result<TripRecordData> = error("unused")
            override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> = error("unused")
        }
        val index = RecordedPhotoIndex(MemoryPhotoPreviewCache())
        val repository = PhotoUsageTripRecordRepository(server, index)
        fun draft(photo: String) = TripRecordDraft(
            locationId = 1,
            startDate = "2026-10-06",
            mediaObjectKeys = listOf(photo),
            localMedia = listOf(TripRecordMediaDraft(photo, 0, null, localPreviewKey = photo)),
        )
        repository.createTripRecord(draft("a")).getOrThrow()
        assertEquals(setOf("a"), index.ids.value)
        fail = true
        assertTrue(repository.updateTripRecord(1, draft("b")).isFailure)
        assertTrue(repository.deleteTripRecord(1).isFailure)
        assertEquals(setOf("a"), index.ids.value)
        fail = false
        repository.updateTripRecord(1, draft("b")).getOrThrow()
        assertEquals(setOf("b"), index.ids.value)
        repository.deleteTripRecord(1).getOrThrow()
        assertTrue(index.ids.value.isEmpty())
    }
}
