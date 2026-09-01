package com.mapmory.shared.presentation.triprecord.state

import com.mapmory.shared.domain.model.TripRecordSummary
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TripRecordItemUiStateTest {
    @Test
    fun `서버에서_다운로드한_목록_썸네일을_UI_사진으로_변환한다`() {
        val thumbnail = byteArrayOf(0x01, 0x02, 0x03)
        val state = TripRecordSummary(
            id = 101,
            title = "제주 여행",
            regionName = "제주시",
            startDate = "2026-08-27",
            endDate = null,
            thumbnailUrl = "https://bucket.example.com/photo.jpg?signature=fresh",
            thumbnailPreviewBytes = thumbnail,
        ).toTripRecordItemUiState()

        assertEquals(1, state.photos.size)
        assertEquals("thumbnail-101", state.photos.single().id)
        assertContentEquals(thumbnail, state.photos.single().previewBytes?.bytesForDecoding())
    }
}
