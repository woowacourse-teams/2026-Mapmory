package com.mapmory.shared.presentation.group

import kotlin.test.*
import com.mapmory.shared.domain.group.AlbumEntry
import com.mapmory.shared.domain.group.AlbumPhoto

class AlbumNavigationStateTest {
    @Test fun gestureBackAndForwardWalkThroughPickerPhotoAndAlbumOneAtATime() {
        val navigation = AlbumNavigationState()
        navigation.showAlbum(album())
        navigation.selectedPhoto.value = "photo"
        navigation.pick.value = true

        assertTrue(navigation.gestureBack())
        assertFalse(navigation.pick.value)
        assertTrue(navigation.gestureBack())
        assertNull(navigation.selectedPhoto.value)
        assertTrue(navigation.gestureBack())
        assertNull(navigation.detail.value)
        assertFalse(navigation.gestureBack())

        assertTrue(navigation.gestureForward())
        assertNotNull(navigation.detail.value)
        assertNull(navigation.selectedPhoto.value)
        assertTrue(navigation.gestureForward())
        assertEquals("photo", navigation.selectedPhoto.value)
        assertTrue(navigation.gestureForward())
        assertTrue(navigation.pick.value)
        assertFalse(navigation.gestureForward())
    }

    @Test fun cancelledGesturesRestoreTheExactAlbumAndPhoto() {
        val navigation = AlbumNavigationState()
        navigation.showAlbum(album())
        navigation.selectedPhoto.value = "photo"
        assertTrue(navigation.gestureBack())
        assertTrue(navigation.gestureForward())
        assertEquals("photo", navigation.selectedPhoto.value)
        assertEquals(album(), navigation.detail.value)

        assertTrue(navigation.gestureBack())
        assertTrue(navigation.gestureForward())
        assertTrue(navigation.gestureBack())
        assertNull(navigation.selectedPhoto.value)
        assertNotNull(navigation.detail.value)
    }

    @Test fun selectingAnotherAlbumDiscardsGestureForwardHistory() {
        val navigation = AlbumNavigationState()
        navigation.showAlbum(album())
        assertTrue(navigation.gestureBack())
        navigation.showAlbum(album().copy(id = 99))
        assertFalse(navigation.gestureForward())
        assertEquals(99L, navigation.detail.value?.id)
    }

    @Test fun selectingAnotherAlbumInvalidatesPendingRestoreBeforeItsResponseArrives() {
        val navigation = AlbumNavigationState()
        val restore = navigation.beginAlbumRequest()
        val selection = navigation.beginAlbumRequest()
        assertNotEquals(restore, navigation.revision)
        assertEquals(selection, navigation.revision)
        navigation.showAlbum(album().copy(id = 99))
        if (restore == navigation.revision) navigation.showAlbum(album())
        assertEquals(99L, navigation.detail.value?.id)
    }

    @Test fun deletedAlbumClosesPhotoAndAnyOpenEditor() {
        val navigation = AlbumNavigationState()
        navigation.showAlbum(album())
        navigation.selectedPhoto.value = "photo"
        navigation.pick.value = true
        navigation.add.value = true
        navigation.rename.value = true
        navigation.showList()
        assertNull(navigation.detail.value)
        assertNull(navigation.selectedPhoto.value)
        assertFalse(navigation.pick.value)
        assertFalse(navigation.rename.value)
        assertFalse(navigation.add.value)
    }

    @Test fun refreshPreservesPhotoIdentityAcrossSignedUrlChangesAndClosesDeletedPhoto() {
        val navigation = AlbumNavigationState()
        val detail = album()
        val entry = AlbumEntry(7, detail.opener, null, null, "2026-10-29", null, null,
            listOf(AlbumPhoto(12, "old-url", 300, 0)), "2026-10-29T12:00:00")
        navigation.showAlbum(detail.copy(entries = listOf(entry)))
        navigation.selectedPhoto.value = "old-url"
        assertFalse(navigation.showAlbum(detail.copy(entries = listOf(entry.copy(photos = listOf(AlbumPhoto(12, "new-url", 300, 0)))))))
        assertEquals("new-url", navigation.selectedPhoto.value)
        navigation.showAlbum(detail)
        assertNull(navigation.selectedPhoto.value)
    }

    @Test fun backDismissesAddChoiceWithoutLeavingAlbum() {
        val navigation = AlbumNavigationState()
        navigation.showAlbum(album())
        navigation.add.value = true
        assertTrue(navigation.back())
        assertFalse(navigation.add.value)
        assertNotNull(navigation.detail.value)
    }

    @Test fun refreshDoesNotCountAsAnotherAlbumEntry() {
        val navigation = AlbumNavigationState()
        assertTrue(navigation.showAlbum(album()))
        assertFalse(navigation.showAlbum(album().copy(name = "수정된 이름")))
        assertTrue(navigation.back())
        assertTrue(navigation.showAlbum(album()))
    }

    @Test fun backClosesDialogThenPhotoThenAlbumBeforeLeavingGroup() {
        val navigation = AlbumNavigationState()
        navigation.showAlbum(album())
        navigation.selectedPhoto.value = "photo"
        navigation.pick.value = true
        assertTrue(navigation.back())
        assertFalse(navigation.pick.value)
        assertEquals("photo", navigation.selectedPhoto.value)
        assertTrue(navigation.back())
        assertNull(navigation.selectedPhoto.value)
        assertNotNull(navigation.detail.value)
        assertTrue(navigation.back())
        assertNull(navigation.detail.value)
        assertFalse(navigation.back())
    }
}
