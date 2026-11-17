package com.mapmory.android.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashlyticsEventContextTest {
    private fun context(name: String, vararg parameters: Pair<String, String>) =
        requireNotNull(CrashlyticsEventContext.make(name, mapOf(*parameters)))

    @Test fun screenContextExcludesRecordIdentifier() {
        val result = context("app_screen_view", "screen_name" to "record_detail", "record_id" to "private_record")
        assertEquals("record_detail", result.keys["screen_name"])
        assertFalse(result.log.contains("private_record"))
    }

    @Test fun missingOrInvalidScreenClearsPreviousScreen() {
        assertEquals("unknown", context("app_screen_view").keys["screen_name"])
        assertEquals("unknown", context("app_screen_view", "screen_name" to "bad\n").keys["screen_name"])
        assertNull(context("record_viewed").keys["screen_name"])
    }

    @Test fun failureContextContainsUsefulStateAndNormalizedNumbers() {
        val result = context("app_error", "operation" to "photo_display", "error_type" to "IllegalStateException",
            "error_category" to "client_logic", "http_status" to "503", "duration_ms" to "00012", "platform_code" to "-11800")
        assertEquals("photo_display", result.keys["last_operation"])
        assertEquals("IllegalStateException", result.keys["last_error_type"])
        assertTrue(result.log.contains("http_status=503"))
        assertTrue(result.log.contains("duration_ms=12"))
        assertTrue(result.log.contains("platform_code=-11800"))
        assertEquals("2", result.keys["event_schema_version"])
    }

    @Test fun nextEventClearsOldErrorAndHttpStatus() {
        val failure = context("app_error", "operation" to "photo_display", "error_type" to "IOException", "http_status" to "503")
        val success = context("app_operation_completed", "operation" to "record_create", "outcome" to "success")
        val state = failure.keys + success.keys
        assertEquals("unset", state["last_error_type"])
        assertEquals("unset", state["last_http_status"])
        assertEquals("record_create", state["last_operation"])
    }

    @Test fun existingPhotoCountIsLogged() {
        assertTrue(context("photos_added", "count" to "8").log.contains("count=8"))
    }

    @Test fun unlistedValuesNeverReachCustomContext() {
        val values = mapOf("record_id" to "private_record", "user_id" to "private_user", "token" to "private_token",
            "title" to "private_title", "message" to "private_message", "url" to "https://private.example/file",
            "latitude" to "37.5", "longitude" to "127.1", "path" to "/private/photo.jpg",
            "error_message" to "private_exception", "event_schema_version" to "private_schema")
        val result = requireNotNull(CrashlyticsEventContext.make("app_error", values))
        values.values.forEach {
            assertFalse(result.log.contains(it))
            assertFalse(result.keys.containsValue(it))
        }
    }

    @Test fun malformedDimensionsAndMeasurementsAreRejected() {
        listOf("https://private.example/file", "/private/photo.jpg", "user@example.com", "private message",
            "bad\nvalue", "bad\n", "a".repeat(81)).forEach {
            val result = context("app_error", "operation" to it, "http_status" to it)
            assertEquals("unset", result.keys["last_operation"])
            assertEquals("unset", result.keys["last_http_status"])
        }
    }

    @Test fun negativeFractionalAndOverflowingCountsAreRejected() {
        listOf("-1", "1.5", "9223372036854775808").forEach {
            assertEquals("unset", context("photos_added", "count" to it).keys["last_count"])
        }
    }

    @Test fun malformedEventNamesAreRejected() {
        listOf("", "private event", "user@example.com", "a".repeat(65), "event\n").forEach {
            assertNull(CrashlyticsEventContext.make(it, emptyMap()))
        }
    }

    @Test fun teamIsExplicitAndUnknownIsNotFalse() {
        assertEquals("true", CrashlyticsEventContext.teamValue("true"))
        assertEquals("false", CrashlyticsEventContext.teamValue("false"))
        assertEquals("unknown", CrashlyticsEventContext.teamValue(null))
        assertEquals("unknown", CrashlyticsEventContext.teamValue("invalid"))
    }

    @Test fun logAndKeysStayWithinBudgets() {
        val dimensions = listOf("action", "error_category", "error_type", "feature", "media_type", "mode", "operation",
            "origin", "outcome", "owner_hint", "share_type", "source", "stage", "status", "step_name")
        val result = requireNotNull(CrashlyticsEventContext.make("app_error", dimensions.associateWith { "a".repeat(80) }))
        assertTrue(result.log.toByteArray(Charsets.UTF_8).size <= 1024)
        assertTrue(result.keys.size + 2 < 64)
    }

    @Test fun logOrderDoesNotDependOnMapInsertionOrder() {
        assertEquals(context("app_error", "outcome" to "failure", "operation" to "photo_display").log,
            context("app_error", "operation" to "photo_display", "outcome" to "failure").log)
    }
}
