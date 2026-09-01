package com.mapmory.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TripRecordTest {
    @Test
    fun `시작일과_종료일이_같은_당일_여행을_생성할_수_있다`() {
        val date = LocalDate(2026, 8, 7)

        val record = createTripRecord(
            startTripDate = date,
            endTripDate = date,
        )

        assertEquals(date, record.startTripDate)
        assertEquals(date, record.endTripDate)
    }

    @Test
    fun `시작일이_종료일보다_늦으면_여행_기록을_생성할_수_없다`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            createTripRecord(
                startTripDate = LocalDate(2026, 8, 8),
                endTripDate = LocalDate(2026, 8, 7),
            )
        }

        assertEquals("여행 시작일은 종료일보다 늦을 수 없습니다", exception.message)
    }

    @Test
    fun `연도_경계를_넘는_여행을_생성할_수_있다`() {
        val record = createTripRecord(
            startTripDate = LocalDate(2026, 12, 31),
            endTripDate = LocalDate(2027, 1, 1),
        )

        assertTrue(requireNotNull(record.startTripDate) < requireNotNull(record.endTripDate))
    }

    @Test
    fun `시작일과_종료일_없이_여행_기록을_생성할_수_있다`() {
        val record = TripRecord(
            imageUrl = "",
            tripRecordTitle = "날짜 없는 여행",
            tripRecordDescription = null,
            startTripDate = null,
            endTripDate = null,
            location = "서울",
        )

        assertEquals(null, record.startTripDate)
        assertEquals(null, record.endTripDate)
    }

    @Test
    fun `ID는_Long_타입으로_지정한_값을_유지한다`() {
        val record = createTripRecord(id = Long.MAX_VALUE)

        assertEquals(Long.MAX_VALUE, record.id)
    }

    @Test
    fun `자동_생성된_ID는_양수다`() {
        val record = TripRecord(
            imageUrl = "image.jpg",
            tripRecordTitle = "제주도 여행",
            tripRecordDescription = "여행 기록",
            startTripDate = LocalDate(2026, 8, 1),
            endTripDate = LocalDate(2026, 8, 3),
            location = "제주도",
        )

        assertTrue(record.id > 0L)
    }

    private fun createTripRecord(
        id: Long = 1L,
        startTripDate: LocalDate = LocalDate(2026, 8, 1),
        endTripDate: LocalDate = LocalDate(2026, 8, 3),
    ): TripRecord = TripRecord(
        id = id,
        imageUrl = "image.jpg",
        tripRecordTitle = "제주도 여행",
        tripRecordDescription = "여행 기록",
        startTripDate = startTripDate,
        endTripDate = endTripDate,
        location = "제주도",
    )
}
