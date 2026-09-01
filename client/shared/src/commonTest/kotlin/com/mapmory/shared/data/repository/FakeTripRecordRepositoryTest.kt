package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeTripRecordRepositoryTest {
    @Test
    fun `여행_기록을_생성하고_수정하고_삭제한다`() = runSuspend {
        val repository = FakeTripRecordRepository(
            now = { "2026-08-07T00:00:00Z" },
        )

        val created = repository.createTripRecord(
            TripRecordDraft(
                locationId = 101,
                title = "서울 여행",
                content = "한강을 걸었다.",
                startDate = "2026-08-01",
                endDate = null,
                mediaObjectKeys = listOf("records/1/photo.jpg"),
            ),
        ).getOrThrow()

        assertEquals(null, created.endDate)
        assertEquals(0, created.media.single().sortOrder)
        assertEquals(1, repository.getTripRecords(TripRecordQuery()).getOrThrow().totalElements)

        val updated = repository.updateTripRecord(
            created.id,
            TripRecordDraft(
                locationId = 101,
                title = "서울 여름 여행",
                content = "한강을 다시 걸었다.",
                startDate = "2026-08-01",
                endDate = "2026-08-02",
                mediaObjectKeys = emptyList(),
            ),
        ).getOrThrow()

        assertEquals("서울 여름 여행", updated.title)
        assertTrue(repository.deleteTripRecord(created.id).isSuccess)
        assertTrue(repository.getTripRecord(created.id).isFailure)
    }

    @Test
    fun `잘못된_날짜_범위로_생성을_거부한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }

        val result = repository.createTripRecord(
            TripRecordDraft(
                locationId = 101,
                title = "서울 여행",
                content = "한강을 걸었다.",
                startDate = "2026-08-02",
                endDate = "2026-08-01",
                mediaObjectKeys = emptyList(),
            ),
        )

        assertFalse(result.isSuccess)
    }
}
