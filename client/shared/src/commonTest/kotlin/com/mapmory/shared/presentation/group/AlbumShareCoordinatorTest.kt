package com.mapmory.shared.presentation.group

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.NoOpMapmoryAnalytics
import com.mapmory.shared.data.settings.AlbumShareStore
import com.mapmory.shared.data.settings.MemoryAlbumShareStore
import com.mapmory.shared.domain.group.*
import com.mapmory.shared.domain.repository.AlbumRepository
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class AlbumShareCoordinatorTest {
    @Test fun confirmedLeaveInvalidatesQueuedShareAndClearsOnlyThatGroupsJournal() {
        val queued = mutableListOf<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queued += block }
        }
        val owner = CoroutineScope(SupervisorJob() + dispatcher)
        val store = MemoryAlbumShareStore()
        val repository = Repository { Result.success(album()) }
        val shares = AlbumShareCoordinator(repository, store, owner, NoOpMapmoryAnalytics)
        fun drain() { while (queued.isNotEmpty()) queued.removeAt(0).run() }
        try {
            shares.start(3, 7, null)
            shares.forgetGroup(3)
            drain()
            assertEquals(0, repository.creates)
            shares.start(4, 7, null)
            drain()
            shares.forgetGroup(3)
            assertEquals(4, shares.operations.value.single().groupId)
            // A fresh user action after joining again uses the new group epoch.
            shares.start(3, 7, null)
            drain()
            assertEquals(2, repository.creates)
            shares.forgetGroup(3)
            val restored = AlbumShareCoordinator(repository, store, owner, NoOpMapmoryAnalytics)
            assertEquals(4, restored.operations.value.single().groupId)
        } finally { owner.cancel(); drain() }
    }

    @Test fun navigatingAwayDoesNotCancelTheAppOwnedWrite() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<Result<AlbumDetail>>()
        val store = MemoryAlbumShareStore()
        val repo = Repository { response.await() }
        val analytics = Events()
        val shares = AlbumShareCoordinator(repo, store, owner, analytics)
        try {
            screen.launch { shares.start(3, 7, null) }.join()
            assertTrue(shares.isRunning)
            assertTrue(checkNotNull(store.read()).contains("RUNNING"))
            screen.cancel()
            response.complete(Result.success(album()))
            assertEquals(AlbumShareStatus.SUCCESS, shares.operations.value.single().status)
            assertEquals(1, repo.creates)
            val restored = AlbumShareCoordinator(repo, store, owner, analytics)
            assertEquals(AlbumShareStatus.SUCCESS, restored.operations.value.single().status)
            assertEquals(1, analytics.events.count { it == "album_share_result" })
        } finally { screen.cancel(); owner.cancel() }
    }

    @Test fun interruptedCreateRestoresAsUnknownAndDoesNotRepeat() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = MemoryAlbumShareStore()
        val repo = Repository { awaitCancellation() }
        val shares = AlbumShareCoordinator(repo, store, owner, NoOpMapmoryAnalytics)
        shares.start(3, 7, null)
        owner.cancel()
        val resumedOwner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val restored = AlbumShareCoordinator(repo, store, resumedOwner, NoOpMapmoryAnalytics)
            assertEquals(AlbumShareStatus.UNKNOWN, restored.operations.value.single().status)
            restored.start(3, 7, null)
            assertEquals(1, repo.creates)
        } finally { resumedOwner.cancel() }
    }

    @Test fun processDeathJournalIsUnknownWithoutAutomaticNetworkCalls() {
        val store = MemoryAlbumShareStore().apply {
            write(Json.encodeToString(listOf(AlbumShareOperation(3, 7, null, AlbumShareStatus.RUNNING))))
        }
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository { Result.success(album()) }
        try {
            val restored = AlbumShareCoordinator(repo, store, owner, NoOpMapmoryAnalytics)
            assertEquals(AlbumShareStatus.UNKNOWN, restored.operations.value.single().status)
            assertEquals(0, repo.creates)
        } finally { owner.cancel() }
    }

    @Test fun unknownCreateOnlyBlocksTheSameRecordAndTarget() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository { Result.failure(IllegalStateException("response lost")) }
        val shares = AlbumShareCoordinator(repo, MemoryAlbumShareStore(), owner, NoOpMapmoryAnalytics)
        try {
            shares.start(3, 7, null)
            shares.start(3, 7, null)
            assertEquals(1, repo.creates)
            shares.start(3, 8, null)
            assertEquals(2, repo.creates)
            shares.start(3, 7, 4)
            assertEquals(1, repo.adds)
            val unknown = shares.operations.value.first { it.recordId == 7L && it.targetAlbumId == null }
            shares.acknowledge(unknown)
            assertTrue(shares.operations.value.none { it == unknown })
            assertEquals(2, repo.creates) // acknowledgement itself never submits another write
        } finally { owner.cancel() }
    }

    @Test fun pendingJobsAreClearedOnAccountChangeAndNewWritesAreGated() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var allowed = true
        val repo = Repository { Result.success(album()) }
        val store = MemoryAlbumShareStore()
        val shares = AlbumShareCoordinator(repo, store, owner, NoOpMapmoryAnalytics) { allowed }
        try {
            shares.start(3, 7, null)
            shares.clearForAccountChange()
            allowed = false
            shares.start(3, 8, null)
            assertTrue(shares.operations.value.isEmpty())
            assertEquals("[]", store.read())
            assertEquals(1, repo.creates)
        } finally { owner.cancel() }
    }

    @Test fun queuedOperationCannotCrossAnAccountChange() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<Result<AlbumDetail>>()
        val repo = Repository { response.await() }
        val shares = AlbumShareCoordinator(repo, MemoryAlbumShareStore(), owner, NoOpMapmoryAnalytics)
        try {
            shares.start(3, 7, null)
            shares.start(3, 8, null)
            shares.clearForAccountChange()
            response.complete(Result.success(album()))
            assertEquals(1, repo.creates)
            assertTrue(shares.operations.value.isEmpty())
        } finally { owner.cancel() }
    }

    @Test fun brokenJournalBlocksWritesInsteadOfLosingDuplicateProtection() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository { Result.success(album()) }
        val store = MemoryAlbumShareStore().apply { write("invalid json") }
        try {
            val shares = AlbumShareCoordinator(repo, store, owner, NoOpMapmoryAnalytics)
            shares.start(3, 7, null)
            assertNotNull(shares.storageError.value)
            assertEquals(0, repo.creates)
        } finally { owner.cancel() }
    }

    @Test fun failedJournalWriteDoesNotSendTheRequest() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repository { Result.success(album()) }
        val store = object : AlbumShareStore {
            override fun read(): String? = null
            override fun write(value: String) { error("disk unavailable") }
        }
        try {
            val shares = AlbumShareCoordinator(repo, store, owner, NoOpMapmoryAnalytics)
            shares.start(3, 7, null)
            assertNotNull(shares.storageError.value)
            assertEquals(0, repo.creates)
        } finally { owner.cancel() }
    }

    private class Events : MapmoryAnalytics {
        val events = mutableListOf<String>()
        override fun logEvent(name: String, parameters: Map<String, String>) { events += name }
        override fun setUserProperty(name: String, value: String?) = Unit
    }

    private class Repository(val createResult: suspend () -> Result<AlbumDetail>) : AlbumRepository {
        var creates = 0
        var adds = 0
        override suspend fun create(groupId: Long, recordId: Long, name: String?, regionId: Long?): Result<AlbumDetail> {
            creates++
            return createResult()
        }
        override suspend fun addRecord(groupId: Long, albumId: Long, recordId: Long): Result<AlbumDetail> {
            adds++
            return Result.success(album())
        }
        override suspend fun list(groupId: Long, page: Int): Result<AlbumPage> = error("unused")
        override suspend fun detail(groupId: Long, albumId: Long): Result<AlbumDetail> = error("unused")
        override suspend fun update(groupId: Long, albumId: Long, name: String?, regionId: Long?): Result<AlbumDetail> = error("unused")
    }
}

internal fun album(id: Long = 4) = AlbumDetail(id, 3, "여행", AlbumRegion(1, "11", "CITY", "서울"), null, null, AlbumMember(9, "나", true), listOf(7), emptyList())
