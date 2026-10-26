package com.mapmory.shared.presentation.group

import androidx.compose.runtime.saveable.SaverScope
import com.mapmory.shared.domain.model.RecordPhotoRemoval
import kotlin.test.*

class GroupPreviewStateTest {
    @Test fun `failure retry and restore preserve the save target input and stage`() {
        val state = GroupPreviewState().apply {
            groupId = "g"; albumId = "second-album"; selectedPhotos = listOf("a", "b")
            startUpload(); advanceSave(); openSheet(GroupSheet.FAILED)
        }
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(GroupPreviewState.Saver) { scope.save(state) }!!
        val restored = GroupPreviewState.Saver.restore(saved)!!
        restored.albumId = "different-album" // Work destination is captured when starting, not read at completion.
        restored.retrySave()
        assertEquals(GroupSaveStage.RECORD, restored.saveStage)
        assertEquals(listOf("a", "b"), (restored.job!!.input as GroupSaveInput.Photos).ids)
        restored.advanceSave(); restored.advanceSave(); restored.showSavedAlbum()
        assertEquals("second-album", restored.albumId)
        assertEquals("g", restored.groupId)
    }

    @Test fun `a save cannot run without its target and input`() {
        assertFailsWith<IllegalStateException> { GroupPreviewState().openSheet(GroupSheet.SAVING) }
        assertFailsWith<IllegalStateException> { GroupPreviewState().startUpload() }
        val state = GroupPreviewState().apply { groupId = "g" }
        assertFailsWith<IllegalArgumentException> { state.startUpload() }
        assertFailsWith<IllegalStateException> { state.shareRecord(2) }
        state.shareRecord(1)
        assertEquals(GroupSaveStage.LINK, state.saveStage)
        assertIs<GroupSaveInput.Record>(state.job!!.input)
    }

    @Test fun `upload input cannot change when callers mutate their photo lists`() {
        val source = mutableListOf("a", "b")
        val job = GroupSaveJob.upload(GroupSaveTarget.NewAlbum("g"), source)
        source.clear()
        val photos = job.input as GroupSaveInput.Photos
        (photos.ids as MutableList).clear()
        assertEquals(listOf("a", "b"), photos.ids)
    }

    @Test fun `deletion uses complete records and removes only the owners photo`() {
        val state = GroupPreviewState()
        val photos = previewGroup.albums.first().photos
        assertIs<RecordPhotoRemoval.PhotoRemoved>(state.removalResult(photos[0]))
        state.pendingDeleteId = photos[0].id
        state.deletePendingPhoto()
        assertIs<RecordPhotoRemoval.RecordDeleted>(state.removalResult(photos[2]))
        state.pendingDeleteId = photos[2].id
        state.deletePendingPhoto()
        assertFalse(state.records.any { it.recordId == 1L })
        assertEquals(listOf("2", "4"), state.visiblePreviewGroup.albums.first().photos.map { it.id })
        assertFailsWith<IllegalArgumentException> { state.removalResult(photos[1]) }
    }
}
