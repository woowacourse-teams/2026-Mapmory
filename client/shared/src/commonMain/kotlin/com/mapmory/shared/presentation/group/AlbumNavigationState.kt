package com.mapmory.shared.presentation.group

import androidx.compose.runtime.mutableStateOf
import com.mapmory.shared.domain.group.AlbumDetail

internal class AlbumNavigationState {
    val detail = mutableStateOf<AlbumDetail?>(null)
    val selectedPhoto = mutableStateOf<String?>(null)
    val pick = mutableStateOf(false)
    val add = mutableStateOf(false)
    val rename = mutableStateOf(false)

    var revision: Long = 0
        private set

    private data class Snapshot(
        val detail: AlbumDetail?,
        val photo: String?,
        val pick: Boolean,
        val add: Boolean,
        val rename: Boolean,
    )
    private data class ForwardEntry(val from: Snapshot, val to: Snapshot)
    private val forward = mutableListOf<ForwardEntry>()

    private fun snapshot() = Snapshot(detail.value, selectedPhoto.value, pick.value, add.value, rename.value)

    fun clearForward() { forward.clear() }

    fun beginAlbumRequest(): Long {
        clearForward()
        return ++revision
    }

    fun gestureBack(): Boolean {
        val previous = snapshot()
        if (!pop()) return false
        forward += ForwardEntry(snapshot(), previous)
        return true
    }

    fun gestureForward(): Boolean {
        val entry = forward.lastOrNull() ?: return false
        if (snapshot() != entry.from) {
            clearForward()
            return false
        }
        forward.removeAt(forward.lastIndex)
        detail.value = entry.to.detail
        selectedPhoto.value = entry.to.photo
        pick.value = entry.to.pick
        add.value = entry.to.add
        rename.value = entry.to.rename
        revision++
        return true
    }

    fun showList() {
        clearForward()
        revision++
        selectedPhoto.value = null
        detail.value = null
        pick.value = false
        add.value = false
        rename.value = false
    }

    /** Returns true only when this response enters a different album screen. */
    fun showAlbum(album: AlbumDetail): Boolean {
        val entered = detail.value?.id != album.id
        if (entered) {
            clearForward()
            revision++
        }
        val selectedId = detail.value?.entries?.flatMap { it.photos }?.find { it.viewUrl == selectedPhoto.value }?.id
        selectedPhoto.value = if (entered || selectedId == null) null
            else album.entries.flatMap { it.photos }.find { it.id == selectedId }?.viewUrl
        detail.value = album
        return entered
    }

    fun back(): Boolean {
        clearForward()
        return pop()
    }

    private fun pop(): Boolean {
        val handled = when {
            rename.value -> { rename.value = false; true }
            add.value -> { add.value = false; true }
            pick.value -> { pick.value = false; true }
            selectedPhoto.value != null -> { selectedPhoto.value = null; true }
            detail.value != null -> { detail.value = null; true }
            else -> false
        }
        if (handled) revision++
        return handled
    }
}
