package com.mapmory.shared.presentation.group

import androidx.compose.runtime.mutableStateOf
import com.mapmory.shared.domain.group.AlbumDetail

internal class AlbumNavigationState {
    val detail = mutableStateOf<AlbumDetail?>(null)
    val selectedPhoto = mutableStateOf<String?>(null)
    val pick = mutableStateOf(false)
    val rename = mutableStateOf(false)

    var revision: Long = 0
        private set

    fun showList() {
        revision++
        selectedPhoto.value = null
        detail.value = null
    }

    /** Returns true only when this response enters a different album screen. */
    fun showAlbum(album: AlbumDetail): Boolean {
        val entered = detail.value?.id != album.id
        detail.value = album
        return entered
    }

    fun back(): Boolean {
        val handled = when {
            rename.value -> { rename.value = false; true }
            pick.value -> { pick.value = false; true }
            selectedPhoto.value != null -> { selectedPhoto.value = null; true }
            detail.value != null -> { detail.value = null; true }
            else -> false
        }
        if (handled) revision++
        return handled
    }
}
