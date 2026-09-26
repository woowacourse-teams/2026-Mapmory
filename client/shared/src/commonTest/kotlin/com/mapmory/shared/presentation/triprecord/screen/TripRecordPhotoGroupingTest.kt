package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class TripRecordPhotoGroupingTest {
    @Test
    fun `사진은 촬영일 최신순으로 묶이고 그룹 안에서는 정렬 순서를 유지한다`() {
        val result = groupTripRecordPhotosByDate(
            photos = listOf(
                photo(id = "older-second", capturedAt = "2026.07.05", sortOrder = 3),
                photo(id = "newest", capturedAt = "2026-07-08", sortOrder = 0),
                photo(id = "older-first", capturedAt = "2026.07.05", sortOrder = 1),
            ),
            fallbackDate = "2026-07-01",
        )

        assertEquals(listOf("2026. 07. 08", "2026. 07. 05"), result.map { it.displayDate })
        assertEquals(listOf("older-first", "older-second"), result[1].photos.map { it.id })
    }

    @Test
    fun `촬영일이 없는 사진은 기록 시작일로 묶는다`() {
        val result = groupTripRecordPhotosByDate(
            photos = listOf(photo(id = "fallback", capturedAt = null, sortOrder = 0)),
            fallbackDate = "2026-09-26",
        )

        assertEquals("2026. 09. 26", result.single().displayDate)
    }

    @Test
    fun `촬영일과 기록일이 모두 없으면 날짜 미상 그룹을 마지막에 둔다`() {
        val result = groupTripRecordPhotosByDate(
            photos = listOf(
                photo(id = "unknown", capturedAt = null, sortOrder = 0),
                photo(id = "known", capturedAt = "2026.09.26", sortOrder = 1),
            ),
            fallbackDate = null,
        )

        assertEquals(listOf("2026. 09. 26", "날짜 미상"), result.map { it.displayDate })
    }

    private fun photo(
        id: String,
        capturedAt: String?,
        sortOrder: Int,
    ) = TripRecordPhotoUiState(
        id = id,
        displayName = id,
        previewBytes = null,
        sortOrder = sortOrder,
        capturedAt = capturedAt,
    )
}
