package com.mapmory.shared.presentation.group

import androidx.lifecycle.ViewModel

/** Lives with the group back stack entry while a record editor covers it. */
internal class RetainedGroupNavigationState(
    val group: LiveGroupState,
) : ViewModel() {
    private val albums = mutableMapOf<Long?, AlbumNavigationState>()

    fun album(groupId: Long?): AlbumNavigationState =
        albums.getOrPut(groupId) { AlbumNavigationState() }
}
