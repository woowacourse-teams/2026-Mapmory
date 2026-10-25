package com.mapmory.shared.analytics

import com.mapmory.shared.presentation.triprecord.share.AnalyticsTripTimelineVideoNotifier
import com.mapmory.shared.presentation.triprecord.share.TripTimelineVideoNotifier
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ShareVideoNotifierAnalyticsTest {
    private class Notifier : TripTimelineVideoNotifier {
        var allowed = false
        var error: Exception? = null
        override fun requestPermissionIfNeeded() { error?.let { throw it } }
        override fun showProgress(jobId: Long) { error?.let { throw it } }
        override fun clearProgress(jobId: Long) { error?.let { throw it } }
        override suspend fun showReady(jobId: Long): Boolean { error?.let { throw it }; return allowed }
    }

    @Test fun `denied notification is not a delivery success or app error`() = runBlocking {
        val analytics = RecordingAnalytics(); val delegate = Notifier()
        val notifier = AnalyticsTripTimelineVideoNotifier(delegate, ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE))
        assertFalse(notifier.showReady(123456789))
        delegate.allowed = true
        assertTrue(notifier.showReady(123456789))
        assertEquals(listOf("not_posted", "posted"), analytics.events
            .filter { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED }.map { it.second["outcome"] })
        assertTrue(analytics.events.none { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        assertFalse(analytics.events.toString().contains("123456789"))
    }

    @Test fun `notification failures report the right stage and preserve original exception`() = runBlocking {
        val analytics = RecordingAnalytics(); val delegate = Notifier()
        val error = IllegalStateException("private platform description"); delegate.error = error
        val notifier = AnalyticsTripTimelineVideoNotifier(delegate, ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE))
        assertSame(error, assertFailsWith<IllegalStateException> { notifier.requestPermissionIfNeeded() })
        assertSame(error, assertFailsWith<IllegalStateException> { notifier.showReady(1) })
        assertEquals(listOf("notification_permission", "notification_ready"), analytics.events
            .filter { it.first == MapmoryAnalyticsEvent.APP_ERROR }.map { it.second["stage"] })
        assertFalse(analytics.events.toString().contains("private"))
    }
}
