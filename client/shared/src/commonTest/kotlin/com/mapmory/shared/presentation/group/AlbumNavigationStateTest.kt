package com.mapmory.shared.presentation.group

import kotlin.test.*
import com.mapmory.shared.domain.group.AlbumDetail
import com.mapmory.shared.domain.group.AlbumRegion
import com.mapmory.shared.domain.group.AlbumMember

class AlbumNavigationStateTest {
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

internal fun album(id: Long = 4) = AlbumDetail(id, 3, "여행", AlbumRegion(1, "11", "CITY", "서울"), null, null, AlbumMember(9, "나", true), listOf(7), emptyList())
