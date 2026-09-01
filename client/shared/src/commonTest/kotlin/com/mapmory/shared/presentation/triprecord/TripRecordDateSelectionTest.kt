package com.mapmory.shared.presentation.triprecord

import kotlin.test.Test
import kotlin.test.assertEquals

class TripRecordDateSelectionTest {
    @Test
    fun `종료일은_시작일부터_선택할_수_있다`() {
        assertEquals(
            "2026-08-20",
            endDatePickerMinimumDate(
                startDate = "2026-08-20",
                today = "2026-08-28",
            ),
        )
    }

    @Test
    fun `미래의_기존_시작일은_종료일_최소값을_오늘로_보정한다`() {
        assertEquals(
            "2026-08-28",
            endDatePickerMinimumDate(
                startDate = "2026-09-03",
                today = "2026-08-28",
            ),
        )
    }

    @Test
    fun `시작일은_종료일과_오늘_중_빠른_날짜까지_선택할_수_있다`() {
        assertEquals(
            "2026-08-10",
            startDatePickerMaximumDate(
                endDate = "2026-08-10",
                today = "2026-08-28",
            ),
        )
        assertEquals(
            "2026-08-28",
            startDatePickerMaximumDate(
                endDate = "2026-09-03",
                today = "2026-08-28",
            ),
        )
        assertEquals(
            "2026-08-28",
            startDatePickerMaximumDate(
                endDate = null,
                today = "2026-08-28",
            ),
        )
    }

    @Test
    fun `피커_초기값은_시작일과_오늘_사이로_보정한다`() {
        assertEquals(
            "2026-08-20",
            initialSelectableTripRecordDate(
                selectedDate = "2026-08-19",
                fallbackDate = "2026-08-28",
                minimumDate = "2026-08-20",
                maximumDate = "2026-08-28",
            ),
        )
        assertEquals(
            "2026-08-28",
            initialSelectableTripRecordDate(
                selectedDate = "2026-09-03",
                fallbackDate = "2026-08-28",
                minimumDate = "2026-08-28",
                maximumDate = "2026-08-28",
            ),
        )
    }
}
