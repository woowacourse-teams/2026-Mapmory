package com.mapmory.shared.analytics

import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.presentation.triprecord.share.ShareVideoPlaybackException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class OperationAnalyticsTest {
    @Test fun `native playback errors are not attributed to client logic`() {
        val analytics = RecordingAnalytics()
        analytics.reportFailure("share_video_preview_playback", ShareVideoPlaybackException(-11800))
        val failure = analytics.events.single().second
        assertEquals("media_playback", failure["error_category"])
        assertEquals("undetermined", failure["owner_hint"])
        assertEquals("ShareVideoPlaybackException", failure["error_type"])
    }

    @Test fun `failure counts once and never includes raw error data`() = runBlocking {
        val analytics = RecordingAnalytics()
        val error = MapmoryApiException(500, "SECRET_CODE", "private title", "private content", "/private/path", emptyList())
        val result = analytics.observeOperation("record_create") { Result.failure<Unit>(error) }
        assertSame(error, result.exceptionOrNull())
        assertEquals(2, analytics.events.size)
        assertEquals("failure", analytics.events.first().second["outcome"])
        val failure = analytics.events.last().second
        assertEquals("server", failure["error_category"])
        assertEquals("server", failure["owner_hint"])
        assertEquals("500", failure["http_status"])
        assertFalse(analytics.events.toString().contains("private"))
        assertFalse(analytics.events.toString().contains("SECRET_CODE"))
    }

    @Test fun `thrown failures preserve exception and count once`() = runBlocking {
        val analytics = RecordingAnalytics()
        val error = IllegalStateException("secret")
        assertSame(error, assertFailsWith<IllegalStateException> {
            analytics.observeOperation<Unit>("statistics_load") { throw error }
        })
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        assertEquals("mobile", analytics.events.last().second["owner_hint"])
    }

    @Test fun `cancellation is never counted whether thrown or returned`() = runBlocking {
        val analytics = RecordingAnalytics()
        assertFailsWith<CancellationException> {
            analytics.observeOperation<Unit>("place_search") { throw CancellationException() }
        }
        assertFailsWith<CancellationException> {
            analytics.observeOperation<Unit>("place_search") { Result.failure(CancellationException()) }
        }
        assertTrue(analytics.events.isEmpty())
    }

    @Test fun `telemetry failure cannot change successful business result`() = runBlocking {
        val analytics = object : MapmoryAnalytics {
            override fun logEvent(name: String, parameters: Map<String, String>) { error("logger failed") }
            override fun setUserProperty(name: String, value: String?) = Unit
        }
        assertEquals(42, analytics.observeOperation("record_create") { Result.success(42) }.getOrThrow())
        assertEquals("saved", analytics.trackAction("record_share_action", "record_share_save_image", "save_image") { "saved" })
    }

    @Test fun `tracked action records outcomes without leaking error messages`() = runBlocking {
        val analytics = RecordingAnalytics()
        assertEquals("shown", analytics.trackAction("record_share_action", "record_share_share_sheet", "share_sheet") { "shown" })
        val error = IllegalStateException("private record title")
        assertSame(error, assertFailsWith<IllegalStateException> {
            analytics.trackAction("record_share_action", "record_share_save_image", "save_image") { throw error }
        })
        assertEquals(listOf("started", "success", "started", "failure"), analytics.events
            .filter { it.first == "record_share_action" }
            .map { it.second.getValue("outcome") })
        assertFalse(analytics.events.toString().contains("private record title"))
        assertEquals("record_share_save_image", analytics.events.last().second["operation"])
    }

    @Test fun `tracked action propagates cancellation without classifying it as failure`() = runBlocking {
        val analytics = RecordingAnalytics()
        assertFailsWith<CancellationException> {
            analytics.trackAction("record_share_action", "record_share_share_sheet", "share_sheet") {
                throw CancellationException()
            }
        }
        assertEquals(listOf("started", "cancelled"), analytics.events.map { it.second.getValue("outcome") })
        assertFalse(analytics.events.any { it.first == MapmoryAnalyticsEvent.APP_ERROR })
    }

    @Test fun `tracked action can defer error reporting to detailed telemetry`() = runBlocking {
        val analytics = RecordingAnalytics()
        val error = IllegalStateException("private image conversion detail")
        assertSame(error, assertFailsWith<IllegalStateException> {
            analytics.trackAction(
                eventName = "record_share_action",
                operation = "record_share_save_image",
                action = "save_image",
                reportErrors = false,
            ) { throw error }
        })
        assertEquals(listOf("started", "failure"), analytics.events
            .filter { it.first == "record_share_action" }
            .map { it.second.getValue("outcome") })
        assertFalse(analytics.events.any { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        assertFalse(analytics.events.toString().contains("private image conversion detail"))
    }

    @Test fun `request rejection is not attributed to mobile automatically`() {
        val analytics = RecordingAnalytics()
        analytics.reportFailure("record_create", MapmoryApiException(400, "BAD_REQUEST", null, null, null, emptyList()))
        assertEquals("request_rejected", analytics.events.single().second["error_category"])
        assertEquals("undetermined", analytics.events.single().second["owner_hint"])
    }
}

internal class RecordingAnalytics : MapmoryAnalytics {
    override fun setUserProperty(name: String, value: String?) = Unit
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    override fun logEvent(name: String, parameters: Map<String, String>) { events += name to parameters }
}
