package com.mapmory.shared.presentation.group

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmory.shared.domain.group.*
import com.mapmory.shared.domain.repository.GroupRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class GroupEditorReturnTest {
    @Test
    fun returningFromPhotoCreationKeepsTheGroupAlbumAndBackOrder() = runBlocking {
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val visible = mutableStateOf(true)
        var current: RetainedGroupNavigationState? = null
        val scene = ImageComposeScene(100, 100) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                if (visible.value) {
                    val navigation = viewModel { RetainedGroupNavigationState(LiveGroupState(Repository())) }
                    SideEffect { current = navigation }
                }
            }
        }
        var frame = 0L
        fun render() { repeat(10) { scene.render(++frame * 16_666_667L).close() } }
        try {
            render()
            val before = assertNotNull(current)
            before.group.openGroup(81)
            val albumNavigation = before.album(81)
            albumNavigation.showAlbum(album())
            // Opening photo creation removes the group content from composition.
            visible.value = false
            render()
            current = null
            visible.value = true
            render()

            val returned = assertNotNull(current)
            assertSame(before, returned)
            assertEquals(81L, returned.group.group?.id)
            assertSame(albumNavigation, returned.album(81))
            assertEquals(album().id, returned.album(81).detail.value?.id)
            assertTrue(returned.album(81).back())
            assertEquals(81L, returned.group.group?.id)
            assertFalse(returned.album(81).back())
            assertTrue(returned.group.back())
            assertNull(returned.group.group)
        } finally {
            scene.close()
            store.clear()
        }
    }

    private class Repository : GroupRepository {
        override suspend fun getGroup(groupId: Long) = Result.success(GroupDetail(groupId, "그룹", emptyList()))
        override suspend fun groups() = Result.success(listOf(GroupSummary(81, "그룹", 1, 1)))
        override suspend fun createGroup(name: String): Result<GroupDetail> = error("unused")
        override suspend fun renameGroup(groupId: Long, name: String): Result<GroupDetail> = error("unused")
        override suspend fun leaveGroup(groupId: Long): Result<Unit> = error("unused")
        override suspend fun createInvitation(groupId: Long): Result<IssuedGroupInvitation> = error("unused")
        override suspend fun previewInvitation(tokenOrCode: String): Result<GroupInvitationPreview> = error("unused")
        override suspend fun acceptInvitation(tokenOrCode: String): Result<GroupDetail> = error("unused")
    }
}
