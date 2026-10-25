package com.mapmory.shared.analytics

import com.mapmory.shared.app.createAppContainer
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.data.settings.MemoryShareVideoJobStore
import com.mapmory.shared.domain.model.ShareVideoDownload
import com.mapmory.shared.domain.model.ShareVideoJob
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.repository.ShareVideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AnalyticsShareVideoRepositoryTest {
    private class Backend : ShareVideoRepository {
        var current = Result.success(ShareVideoJob(987654321, ShareVideoStatus.PENDING))
        var createError: Exception? = null
        var throwCreateError = false
        override suspend fun createTravelTimeline(): Result<ShareVideoJob> {
            createError?.let { if (throwCreateError) throw it else return Result.failure(it) }
            return current
        }
        override suspend fun getJob(jobId: Long) = current
        override suspend fun getDownloadUrl(jobId: Long) = Result.success(
            ShareVideoDownload(jobId, "https://private.example/video?token=secret", 900),
        )
    }
    private fun RecordingAnalytics.statuses() = events.filter { it.first == MapmoryAnalyticsEvent.SHARE_VIDEO_STATUS_CHANGED }
    private fun RecordingAnalytics.errors() = events.filter { it.first == MapmoryAnalyticsEvent.APP_ERROR }

    @Test fun `accepted request is not completed rendering and normal polls are quiet`() = runBlocking {
        val backend = Backend(); val analytics = RecordingAnalytics(); var now = 1000L
        val repository = AnalyticsShareVideoRepository(backend, analytics, MemoryShareVideoJobStore()) { now }
        repository.createTravelTimeline().getOrThrow()
        assertEquals(listOf("pending"), analytics.statuses().map { it.second["status"] })
        repeat(5) { repository.getJob(987654321) }
        backend.current = Result.success(ShareVideoJob(987654321, ShareVideoStatus.RENDERING))
        repeat(5) { repository.getJob(987654321) }
        now = 5500L
        backend.current = Result.success(ShareVideoJob(987654321, ShareVideoStatus.COMPLETED))
        repeat(5) { repository.getJob(987654321) }
        assertEquals(listOf("pending", "rendering", "completed"), analytics.statuses().map { it.second["status"] })
        assertEquals("4500", analytics.statuses().last().second["elapsed_ms"])
        assertEquals("created_here", analytics.statuses().last().second["origin"])
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED })
        assertTrue(analytics.errors().isEmpty())
        assertFalse(analytics.events.toString().contains("987654321"))
    }

    @Test fun `restored job uses persisted start and is not reported as a new request`() = runBlocking {
        val backend = Backend(); val analytics = RecordingAnalytics(); val store = MemoryShareVideoJobStore()
        store.saveJobId(987654321, 1000L)
        backend.current = Result.success(ShareVideoJob(987654321, ShareVideoStatus.COMPLETED))
        val repository = AnalyticsShareVideoRepository(backend, analytics, store) { 6000L }
        repeat(3) { repository.getJob(987654321) }
        assertEquals("restored", analytics.statuses().single().second["origin"])
        assertEquals("5000", analytics.statuses().single().second["elapsed_ms"])
        assertTrue(analytics.events.none { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_STARTED })
    }

    @Test fun `server generation failure is reported once even if queried repeatedly`() = runBlocking {
        val backend = Backend(); val analytics = RecordingAnalytics()
        backend.current = Result.success(ShareVideoJob(987654321, ShareVideoStatus.FAILED))
        val repository = AnalyticsShareVideoRepository(backend, analytics, MemoryShareVideoJobStore())
        repeat(5) { repository.getJob(987654321) }
        assertEquals(1, analytics.statuses().size)
        assertEquals(1, analytics.errors().size)
        assertEquals("generation", analytics.errors().single().second["stage"])
    }

    @Test fun `status failures deduplicate until recovery without suppressing later failure episodes`() = runBlocking {
        val backend = Backend(); val analytics = RecordingAnalytics()
        val repository = AnalyticsShareVideoRepository(backend, analytics, MemoryShareVideoJobStore())
        val error = MapmoryApiException(503, "private-code", "private", "private", "private", emptyList())
        backend.current = Result.failure(error)
        repeat(5) { assertSame(error, repository.getJob(987654321).exceptionOrNull()) }
        assertEquals(1, analytics.errors().size)
        backend.current = Result.success(ShareVideoJob(987654321, ShareVideoStatus.RENDERING))
        repository.getJob(987654321)
        backend.current = Result.failure(error); repository.getJob(987654321)
        assertEquals(2, analytics.errors().size)
        assertFalse(analytics.events.toString().contains("private"))
    }

    @Test fun `create returns the same failure and propagates thrown failures`() = runBlocking {
        val backend = Backend(); val analytics = RecordingAnalytics()
        val error = IllegalStateException("private body")
        backend.createError = error
        val repository = AnalyticsShareVideoRepository(backend, analytics, MemoryShareVideoJobStore())
        assertSame(error, repository.createTravelTimeline().exceptionOrNull())
        backend.throwCreateError = true
        assertSame(error, assertFailsWith<IllegalStateException> { repository.createTravelTimeline() })
        assertEquals(2, analytics.errors().size)
        assertTrue(analytics.statuses().isEmpty())
    }

    @Test fun `returned cancellation is propagated without app error`() = runBlocking {
        val backend = Backend(); val analytics = RecordingAnalytics()
        backend.createError = CancellationException()
        val repository = AnalyticsShareVideoRepository(backend, analytics, MemoryShareVideoJobStore())
        assertFailsWith<CancellationException> { repository.createTravelTimeline() }
        backend.current = Result.failure(CancellationException())
        assertFailsWith<CancellationException> { repository.getJob(987654321) }
        assertTrue(analytics.errors().isEmpty())
        assertTrue(analytics.statuses().isEmpty())
    }

    @Test fun `actual app container decorates video repository and preserves private download URL`() = runBlocking {
        val analytics = RecordingAnalytics()
        val container = createAppContainer(
            tripRecordRepository = FakeTripRecordRepository(now = { "2026-10-26T00:00:00" }), shareVideoRepository = Backend(), analytics = analytics,
        )
        try {
            val repository = assertNotNull(container.shareVideoRepository)
            repository.createTravelTimeline()
            assertEquals("https://private.example/video?token=secret", repository.getDownloadUrl(987654321).getOrThrow().downloadUrl)
            assertEquals(listOf("create_request", "download_url"), analytics.events
                .filter { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED }.map { it.second["operation"] })
            assertFalse(analytics.events.toString().contains("private"))
            assertFalse(analytics.events.toString().contains("987654321"))
        } finally { container.close() }
    }
}
