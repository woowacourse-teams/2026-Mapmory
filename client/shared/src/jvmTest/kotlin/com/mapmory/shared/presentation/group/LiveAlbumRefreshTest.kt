package com.mapmory.shared.presentation.group

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import com.mapmory.shared.analytics.NoOpMapmoryAnalytics
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.data.settings.MemoryAlbumShareStore
import com.mapmory.shared.domain.group.*
import com.mapmory.shared.domain.repository.AlbumRepository
import kotlinx.coroutines.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LiveAlbumRefreshTest {
    @Test fun recordRevisionRefetchesAndClosesDeletedAlbumInComposedScreen() {
        checkRefresh(MapmoryApiException(404, "ALBUM_NOT_FOUND", null, null, null, emptyList()), expectedAlbum = false, expectedGroupUnavailable = false)
    }

    @Test fun temporaryReadFailureKeepsCurrentAlbum() {
        checkRefresh(IllegalStateException("offline"), expectedAlbum = true, expectedGroupUnavailable = false)
    }

    @Test fun groupAccessLossReturnsToGroupList() {
        checkRefresh(MapmoryApiException(404, "GROUP_NOT_FOUND", null, null, null, emptyList()), expectedAlbum = true, expectedGroupUnavailable = true)
    }

    private fun checkRefresh(failure: Throwable, expectedAlbum: Boolean, expectedGroupUnavailable: Boolean) {
        val repository = Repository()
        val navigation = AlbumNavigationState().apply { showAlbum(album()) }
        val revision = mutableStateOf(0L)
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val shares = AlbumShareCoordinator(repository, MemoryAlbumShareStore(), owner, NoOpMapmoryAnalytics)
        var unavailable = false
        val scene = ImageComposeScene(412, 892) {
            LiveAlbumContent(3, repository, FakeTripRecordRepository(now = { "2026-10-29T12:00:00" }), shares, navigation,
                "그룹", 2, {}, {}, {}, {}, recordRevision = revision.value,
                onGroupUnavailable = { unavailable = true })
        }
        var frame = 0L
        fun render() { repeat(60) { scene.render(++frame * 16_666_667L).close() } }
        try {
            render()
            val initialCalls = repository.detailCalls
            assertTrue(initialCalls > 0)
            repository.failure = failure
            revision.value++
            render()
            assertTrue(repository.detailCalls > initialCalls)
            assertEquals(expectedAlbum, navigation.detail.value != null)
            assertEquals(expectedGroupUnavailable, unavailable)
        } finally { scene.close(); owner.cancel() }
    }

    private class Repository : AlbumRepository {
        var failure: Throwable? = null
        var detailCalls = 0
        override suspend fun list(groupId: Long, page: Int) = Result.success(AlbumPage(emptyList(), 0, 20, 0, 0, false))
        override suspend fun detail(groupId: Long, albumId: Long): Result<AlbumDetail> {
            detailCalls++
            return failure?.let { Result.failure(it) } ?: Result.success(album())
        }
        override suspend fun create(groupId: Long, recordId: Long, name: String?, regionId: Long?): Result<AlbumDetail> = error("unused")
        override suspend fun addRecord(groupId: Long, albumId: Long, recordId: Long): Result<AlbumDetail> = error("unused")
        override suspend fun update(groupId: Long, albumId: Long, name: String?, regionId: Long?): Result<AlbumDetail> = error("unused")
    }
}
