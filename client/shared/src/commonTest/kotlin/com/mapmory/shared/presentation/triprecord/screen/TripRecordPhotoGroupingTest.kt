package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class TripRecordPhotoGroupingTest {
    @Test
    fun `9월_11일과_9월_9일_사진은_각_촬영일로_분리한다`() {
        val groups = groupTripRecordPhotosByDate(listOf(
            photo("11일", "2026.09.11", 0),
            photo("9일", "2026-09-09T10:00:00", 1),
            photo("촬영일 없음", null, 2),
        ))
        assertEquals(listOf("2026-09-11", "2026-09-09", null), groups.map { it.sortDate })
        assertEquals(listOf(listOf("11일"), listOf("9일"), listOf("촬영일 없음")), groups.map { it.photos.map { photo -> photo.id } })
    }

    @Test
    fun `사진은 촬영일 최신순으로 묶이고 그룹 안에서는 정렬 순서를 유지한다`() {
        val result = groupTripRecordPhotosByDate(
            photos = listOf(
                photo(id = "older-second", capturedAt = "2026.07.05", sortOrder = 3),
                photo(id = "newest", capturedAt = "2026-07-08", sortOrder = 0),
                photo(id = "older-first", capturedAt = "2026.07.05", sortOrder = 1),
            ),
        )

        assertEquals(listOf("2026. 07. 08 (수)", "2026. 07. 05 (일)"), result.map { it.displayDate })
        assertEquals(listOf("older-first", "older-second"), result[1].photos.map { it.id })
    }

    @Test
    fun `촬영일이 없는 사진은 기록 시작일을 추측하지 않고 날짜 미상으로 묶는다`() {
        val result = groupTripRecordPhotosByDate(
            photos = listOf(photo(id = "fallback", capturedAt = null, sortOrder = 0)),
        )

        assertEquals("날짜 미상", result.single().displayDate)
    }

    @Test
    fun `촬영일과 기록일이 모두 없으면 날짜 미상 그룹을 마지막에 둔다`() {
        val result = groupTripRecordPhotosByDate(
            photos = listOf(
                photo(id = "unknown", capturedAt = null, sortOrder = 0),
                photo(id = "known", capturedAt = "2026.09.26", sortOrder = 1),
            ),
        )

        assertEquals(listOf("2026. 09. 26 (토)", "날짜 미상"), result.map { it.displayDate })
    }

    @Test
    fun `유효하지 않은 촬영일은 날짜 미상으로 표시한다`() {
        val result = groupTripRecordPhotosByDate(
            photos = listOf(photo(id = "invalid-date", capturedAt = "2026.13.40", sortOrder = 0)),
        )

        assertEquals("날짜 미상", result.single().displayDate)
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
