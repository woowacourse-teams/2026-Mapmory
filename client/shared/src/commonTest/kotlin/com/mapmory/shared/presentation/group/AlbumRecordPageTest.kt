package com.mapmory.shared.presentation.group

import com.mapmory.shared.domain.model.TripRecordSummary
import kotlin.test.*

class AlbumRecordPageTest {
    @Test fun overlappingOffsetPagesKeepStableUniqueRecordKeys() {
        fun record(id: Long) = TripRecordSummary(id, "", null, "2026-10-29", null)
        val merged = mergeAlbumRecordPage(listOf(record(3), record(2)), listOf(record(2), record(1)))
        assertEquals(listOf(3L, 2L, 1L), merged.map { it.id })
        assertEquals(merged, mergeAlbumRecordPage(merged, listOf(record(2), record(1))))
    }
}
