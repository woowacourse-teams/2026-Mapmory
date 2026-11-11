package com.mapmory.shared.presentation.group

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mapmory.shared.domain.group.AlbumSummary

/** Lives with the group back stack entry while a record editor covers it. */
internal class RetainedGroupNavigationState(
    val group: LiveGroupState,
) : ViewModel() {
    var lastRefresh = 0
    private val albumLists = mutableMapOf<Long, RetainedAlbumList>()
    fun albumList(groupId: Long): RetainedAlbumList = albumLists.getOrPut(groupId) { RetainedAlbumList() }

    private val albums = mutableMapOf<Long?, AlbumNavigationState>()

    fun removeGroup(groupId: Long) {
        albumLists.remove(groupId)
        albums.remove(groupId)
        group.removeGroup(groupId)
    }

    fun album(groupId: Long?): AlbumNavigationState =
        albums.getOrPut(groupId) { AlbumNavigationState() }
}

internal class RetainedAlbumList {
    var fetchedAt: kotlin.time.TimeMark? = null
    var loadedRecordRevision: Long? = null
    var items by mutableStateOf<List<AlbumSummary>?>(null)
    var page by mutableStateOf(0)
    var more by mutableStateOf(false)
}
