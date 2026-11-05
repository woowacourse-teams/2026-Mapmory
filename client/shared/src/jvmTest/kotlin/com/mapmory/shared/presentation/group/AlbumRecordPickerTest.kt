package com.mapmory.shared.presentation.group

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.presentation.triprecord.screen.TripRecordBackground
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class AlbumRecordPickerTest {
    @Test fun selectingAnUntitledRecordUsesItsIdAndAlreadySharedRecordsCannotBeSelected() {
        val selected = mutableListOf<Long>()
        val scene = ImageComposeScene(412, 892) {
            TripRecordBackground { GroupTheme {
                AlbumRecordPicker(
                    records = listOf(
                        TripRecordSummary(41, "", "서울", "2026-10-28", null, photoCount = 3),
                        TripRecordSummary(42, "", "제주", "2026-10-20", "2026-10-22", photoCount = 12),
                    ),
                    loading = false, error = null, hasMore = false, enabled = true,
                    status = { if (it.id == 42L) "이미 담음" else null },
                    onBack = {}, onSelect = { selected += it.id }, onMore = {}, onRetry = {},
                )
            } }
        }
        var frame = 0L
        fun render() { repeat(30) { scene.render(++frame * 16_666_667L).close() } }
        fun tap(y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(200f, y), type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, Offset(200f, y), type = PointerType.Touch)
            render()
        }
        try {
            render()
            val preview = java.io.File("build/reports/album-record-picker.png")
            preview.parentFile.mkdirs()
            scene.render(++frame * 16_666_667L).use { preview.writeBytes(it.encodeToData()!!.bytes) }
            tap(150f)
            assertEquals(listOf(41L), selected)
            tap(270f)
            assertEquals(listOf(41L), selected)
        } finally { scene.close() }
    }
}
