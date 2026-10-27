package com.mapmory.shared.analytics

import com.mapmory.shared.domain.model.*
import com.mapmory.shared.domain.repository.ShareVideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class VideoAnalyticsTest {
    private class Recorder : MapmoryAnalytics {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        override fun logEvent(name: String, parameters: Map<String, String>) { events += name to parameters }
        override fun setUserProperty(name: String, value: String?) = Unit
    }

    @Test fun terminalResultIsNotDuplicatedByPollingOrCreateResponse() = runBlocking {
        val analytics = Recorder()
        val repository = AnalyticsShareVideoRepository(object : ShareVideoRepository {
            override suspend fun createTravelTimeline(style: ShareVideoStyle) = Result.success(ShareVideoJob(1, ShareVideoStatus.COMPLETED))
            override suspend fun getJob(jobId: Long) = Result.success(ShareVideoJob(jobId, ShareVideoStatus.COMPLETED))
            override suspend fun getDownloadUrl(jobId: Long): Result<ShareVideoDownload> = error("unused")
        }, analytics)
        repository.createTravelTimeline(ShareVideoStyle.CLASSIC)
        repeat(5) { repository.getJob(1) }
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.VIDEO_GENERATION_RESULT })
        assertTrue(analytics.events.none { "job_id" in it.second })
    }

    @Test fun cancellationIsNotReportedAsFailureAndPropagates() = runBlocking {
        val analytics = Recorder()
        assertFailsWith<CancellationException> {
            analytics.videoAction("save") { throw CancellationException() }
        }
        assertEquals(listOf("started", "cancelled"), analytics.events.map { it.second["outcome"] })
    }

    @Test fun telemetryFailureDoesNotFailSaving() = runBlocking {
        val analytics = object : MapmoryAnalytics {
            override fun logEvent(name: String, parameters: Map<String, String>) { error("SDK unavailable") }
            override fun setUserProperty(name: String, value: String?) = Unit
        }
        assertEquals("saved", analytics.videoAction("save") { "saved" })
    }
}
