package com.mapmory.shared.presentation.triprecord.state

import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordSummary
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripRecordItemUiStateTest {
    @Test
    fun `기기 URI만 로컬 원본으로 사용하고 iOS 식별자는 URL로 처리하지 않는다`() {
        val photo = TripRecordPhotoUiState(
            id = "server-key", localPhotoId = "content://media/42",
            displayName = "photo", previewBytes = null, sortOrder = 0,
        )
        assertEquals("content://media/42", photo.localOriginalUri)
        assertNull(photo.copy(localPhotoId = "asset-id/L0/001").localOriginalUri)
        assertNull(photo.copy(localPhotoId = null).localOriginalUri)
    }

    @Test
    fun `상세 사진은 썸네일과 별도로 원본 URL을 보존한다`() {
        val originalUrl = "https://bucket.example.com/original.jpg?signature=fresh"
        val state = TripRecordData(
            id = 101,
            locationId = 1,
            title = "제주 여행",
            content = "",
            startDate = "2026-08-27",
            endDate = null,
            media = listOf(
                TripRecordMedia(
                    id = 1,
                    objectKey = "records/101/original.jpg",
                    sortOrder = 0,
                    url = originalUrl,
                    previewUri = "file:///preview.jpg",
                ),
            ),
            createdAt = "2026-08-27T00:00:00Z",
            updatedAt = "2026-08-27T00:00:00Z",
        ).toTripRecordItemUiState()

        assertEquals("file:///preview.jpg", state.photos.single().previewUri)
        assertEquals(originalUrl, state.photos.single().fullResolutionUri)
    }

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
        assertNull(state.photoCount) // A thumbnail is not the total number of photos.
        assertEquals("thumbnail-101", state.photos.single().id)
        assertContentEquals(thumbnail, state.photos.single().previewBytes?.bytesForDecoding())
    }
}
