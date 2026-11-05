package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSort
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeTripRecordRepositoryTest {
    @Test
    fun `여행_시작일_내림차순으로_정렬한_뒤_페이지를_나눈다`() = runSuspend {
        var createdAtDay = 7
        val repository = FakeTripRecordRepository {
            "2026-08-${createdAtDay++}T00:00:00Z"
        }
        repository.createTripRecord(
            TripRecordDraft(101, "2026-08-15", listOf("recent.jpg"), title = "최근 여행"),
        )
        repository.createTripRecord(
            TripRecordDraft(101, "2026-08-01", listOf("past.jpg"), title = "과거 여행"),
        )

        val firstPage = repository.getTripRecords(
            TripRecordQuery(page = 0, size = 1, sort = TripRecordSort.TRAVEL_DATE_DESC),
        ).getOrThrow()
        val secondPage = repository.getTripRecords(
            TripRecordQuery(page = 1, size = 1, sort = TripRecordSort.TRAVEL_DATE_DESC),
        ).getOrThrow()

        assertEquals("최근 여행", firstPage.records.single().title)
        assertEquals("과거 여행", secondPage.records.single().title)
    }

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
                mediaObjectKeys = listOf("records/1/photo.jpg"),
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
                mediaObjectKeys = listOf("records/1/photo.jpg"),
            ),
        )

        assertFalse(result.isSuccess)
    }
}
