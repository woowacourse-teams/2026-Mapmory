package com.mapmory.shared.analytics

import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.presentation.triprecord.share.ShareMediaPermissionDeniedException
import com.mapmory.shared.presentation.triprecord.share.ShareVideoPlaybackException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ShareMediaAnalyticsTest {
    @Test fun `permission denial and cancellation preserve results without app errors`() = runBlocking {
        val analytics = RecordingAnalytics()
        val telemetry = ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE)
        val permission = ShareMediaPermissionDeniedException("private permission message")
        assertSame(permission, assertFailsWith<ShareMediaPermissionDeniedException> {
            telemetry.observe<Unit>(ShareMediaOperation.SAVE) { throw permission }
        })
        assertFailsWith<CancellationException> {
            telemetry.observe<Unit>(ShareMediaOperation.DOWNLOAD) { throw CancellationException("private path") }
        }
        assertEquals(listOf("permission_denied", "cancelled"), analytics.events
            .filter { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED }.map { it.second["outcome"] })
        assertTrue(analytics.events.none { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        assertFalse(analytics.events.toString().contains("private"))
    }

    @Test fun `expected backend preconditions are not app errors`() = runBlocking {
        val analytics = RecordingAnalytics()
        val telemetry = ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE)
        for (code in listOf("SHARE_VIDEO_NOT_READY", "INSUFFICIENT_TIMELINE_STOPS")) {
            telemetry.observeResult<Unit>(ShareMediaOperation.CREATE_REQUEST) {
                Result.failure(MapmoryApiException(409, code, "private", "private", "private", emptyList()))
            }
        }
        assertTrue(analytics.events.none { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        assertEquals(listOf("not_ready", "insufficient_records"), analytics.events
            .filter { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED }.map { it.second["outcome"] })
    }

    @Test fun `save success and sharing handoff are different outcomes`() = runBlocking {
        val analytics = RecordingAnalytics()
        val telemetry = ShareMediaAnalytics(analytics, ShareMediaSource.ACHIEVEMENT)
        assertEquals(42, telemetry.observe(ShareMediaOperation.SAVE) { 42 })
        telemetry.observe(ShareMediaOperation.SHARE_SHEET) { Unit }
        val events = analytics.events.filter { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED }
        assertEquals(listOf("success", "handoff"), events.map { it.second["outcome"] })
        assertTrue(events.all { it.second["share_type"] == "achievement" && it.second["media_type"] == "image" })
        assertTrue(events.all { it.second["duration_ms"]!!.toLong() >= 0L })
    }

    @Test fun `thrown failures retain identity and send only safe metadata`() = runBlocking {
        val analytics = RecordingAnalytics()
        val telemetry = ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE)
        val error = MapmoryApiException(503, "SECRET_CODE", "secret title", "secret token", "https://secret/video", emptyList())
        assertSame(error, assertFailsWith<MapmoryApiException> {
            telemetry.observe<Unit>(ShareMediaOperation.DOWNLOAD) { throw error }
        })
        val failure = analytics.events.single { it.first == MapmoryAnalyticsEvent.APP_ERROR }.second
        assertEquals("share_video_download", failure["operation"])
        assertEquals("server", failure["error_category"])
        assertEquals("download", failure["stage"])
        assertFalse(analytics.events.toString().contains("secret", ignoreCase = true))
    }

    @Test fun `throwing logger cannot affect business or native preview callbacks`() = runBlocking {
        val broken = object : MapmoryAnalytics {
            override fun logEvent(name: String, parameters: Map<String, String>) { error("logger") }
            override fun setUserProperty(name: String, value: String?) = Unit
        }
        val telemetry = ShareMediaAnalytics(broken, ShareMediaSource.RECORD)
        telemetry.action(ShareMediaAction.OPENED)
        assertEquals(17, telemetry.observe(ShareMediaOperation.SAVE) { 17 })
        val preview = ShareVideoPreviewAnalytics(broken)
        preview.opened(); preview.ready(); preview.playbackChanged(true); preview.completed()
        preview.failed(ShareVideoPlaybackException(-1))
    }

    @Test fun `native preview polls do not duplicate readiness playback or errors`() {
        val analytics = RecordingAnalytics()
        val preview = ShareVideoPreviewAnalytics(analytics)
        preview.opened()
        repeat(10) { preview.ready(); preview.playbackChanged(true) }
        repeat(10) { preview.completed() }
        preview.playbackChanged(true) // Replay.
        preview.playbackChanged(false)
        repeat(10) { preview.failed(ShareVideoPlaybackException(100, -1)) }
        val actions = analytics.events.filter { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_ACTION }.map { it.second["action"] }
        assertEquals(listOf("preview_started", "preview_completed", "preview_started", "preview_paused"), actions)
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        assertEquals("100", analytics.events.single { it.first == MapmoryAnalyticsEvent.APP_ERROR }.second["platform_code"])
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED && it.second["operation"] == "preview_load" })
    }

    @Test fun `load failure and closing an unprepared preview are distinct`() {
        val analytics = RecordingAnalytics()
        ShareVideoPreviewAnalytics(analytics).apply { opened(); failed(ShareVideoPlaybackException(-11800)); failed(ShareVideoPlaybackException(-11800)); closed() }
        ShareVideoPreviewAnalytics(analytics).apply { opened(); closed() }
        assertEquals(listOf("failure", "cancelled"), analytics.events
            .filter { it.first == MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED }.map { it.second["outcome"] })
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.APP_ERROR })
    }
    @Test fun `callbacks after closing a preview do not report ready playback or errors`() {
        val analytics = RecordingAnalytics()
        val preview = ShareVideoPreviewAnalytics(analytics)
        preview.opened(); preview.closed()
        preview.ready(); preview.playbackChanged(true); preview.failed(ShareVideoPlaybackException(-1)); preview.completed()
        assertEquals("cancelled", analytics.events.last().second["outcome"])
        assertEquals(2, analytics.events.size)
    }

    @Test fun `friendly network wrapper remains an IO failure without exposing its cause`() = runBlocking {
        val analytics = RecordingAnalytics()
        val error = com.mapmory.shared.data.remote.MapmoryConnectionException("private message", IllegalStateException("private address"))
        ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE).observeResult<Unit>(ShareMediaOperation.CREATE_REQUEST) {
            Result.failure(error)
        }
        val failure = analytics.events.single { it.first == MapmoryAnalyticsEvent.APP_ERROR }.second
        assertEquals("io", failure["error_category"])
        assertEquals("undetermined", failure["owner_hint"])
        assertFalse(analytics.events.toString().contains("private"))
    }

}
