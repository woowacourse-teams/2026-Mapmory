package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.photo.SelectedPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoPickerListItemTest {
    @Test
    fun `날짜별 사진을 최신순으로 헤더와 두 장씩의 행으로 펼친다`() {
        val items = listOf(
            photo("older-1", "2026-10-06"),
            photo("unknown", null),
            photo("newest-1", "2026-10-07"),
            photo("older-2", "2026-10-06"),
            photo("newest-2", "2026-10-07"),
            photo("newest-3", "2026-10-07"),
        ).toPhotoPickerListItems(columnCount = 2)

        assertEquals(
            listOf(
                "date:2026-10-07",
                "row:newest-1",
                "row:newest-3",
                "date:2026-10-06",
                "row:older-1",
                "date:촬영일 미상",
                "row:unknown",
            ),
            items.map(PhotoPickerListItem::key),
        )

        val rows = items.filterIsInstance<PhotoPickerListItem.PhotoRow>()
        assertTrue(rows.all { it.photos.size in 1..2 })
        assertEquals(
            listOf("newest-1", "newest-2", "newest-3", "older-1", "older-2", "unknown"),
            rows.flatMap { row -> row.photos }.map(SelectedPhoto::id),
        )
        assertEquals(
            listOf(true, false, true, true),
            rows.map(PhotoPickerListItem.PhotoRow::isFirstRowInDate),
        )
    }

    @Test
    fun `열_수에_맞춰_사진을_행으로_나눈다`() {
        val photos = (1..5).map { photo("photo-$it", "2026-10-07") }

        val oneColumnRows = photos.toPhotoPickerListItems(columnCount = 1)
            .filterIsInstance<PhotoPickerListItem.PhotoRow>()
        val fourColumnRows = photos.toPhotoPickerListItems(columnCount = 4)
            .filterIsInstance<PhotoPickerListItem.PhotoRow>()

        assertEquals(listOf(1, 1, 1, 1, 1), oneColumnRows.map { it.photos.size })
        assertEquals(listOf(4, 1), fourColumnRows.map { it.photos.size })
    }

    private fun photo(id: String, capturedAt: String?) =
        SelectedPhoto(id, "$id.jpg", previewBytes = null, capturedAt = capturedAt)
}
