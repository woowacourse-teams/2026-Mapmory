package com.mapmory.shared.presentation.group

import androidx.compose.runtime.saveable.SaverScope
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.domain.group.GroupRuleException
import com.mapmory.shared.domain.group.GroupRuleViolation
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class GroupAnalyticsTest {
    private val logger = CapturingAnalytics()
    private val telemetry = GroupAnalytics(logger)
    private val state = GroupPreviewState().apply { preview = true; groupId = "private-group" }
    private fun <T> act(action: GroupAction, block: () -> T) = telemetry.action(action, state::analyticsContext, block)

    @Test fun `real state transitions track retry progress completion and deletion only as preview outcomes`() {
        state.albumId = "private-album"
        state.selectedPhotos = listOf("private-photo")
        act(GroupAction.PHOTO_SAVE_SUBMITTED) { state.startUpload() }
        act(GroupAction.SAVE_ADVANCED) { state.advanceSave() }
        act(GroupAction.FAILURE_SIMULATED) { state.openSheet(GroupSheet.FAILED) }
        act(GroupAction.SAVE_RETRIED) { state.retrySave() }
        act(GroupAction.SAVE_ADVANCED) { state.advanceSave() }
        act(GroupAction.SAVE_ADVANCED) { state.advanceSave() }
        assertEquals(listOf("save_started", "save_progressed", "save_failed", "save_retried", "save_progressed", "save_completed"),
            logger.parameters(MapmoryAnalyticsEvent.GROUP_PREVIEW_RESULT).map { it["outcome"] })
        assertEquals(6, logger.parameters(MapmoryAnalyticsEvent.GROUP_ACTION).size)
        assertTrue(logger.parameters(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
        assertFalse(logger.events.any { it.first in setOf("record_save_completed", "record_deleted", "app_operation_completed") })
        assertFalse(logger.events.toString().contains("private-"))
    }

    @Test fun `invalid save reports one exception with context and preserves exception semantics`() {
        state.selectedPhotos = emptyList()
        assertFailsWith<IllegalArgumentException> { act(GroupAction.PHOTO_SAVE_SUBMITTED) { state.startUpload() } }
        val errors = logger.parameters(MapmoryAnalyticsEvent.APP_ERROR)
        assertEquals(1, errors.size)
        assertEquals("group_action", errors.single()["operation"])
        assertEquals("photo_save_submitted", errors.single()["action"])
        assertEquals("client_logic", errors.single()["error_category"])
        assertEquals("preview", errors.single()["execution_mode"])
        assertTrue(logger.parameters(MapmoryAnalyticsEvent.GROUP_PREVIEW_RESULT).isEmpty())
    }

    @Test fun `raw inputs tokens and exception text never appear in telemetry`() {
        state.name = "PRIVATE_NAME"
        state.code = "987654"
        state.pendingDeleteId = "PRIVATE_PHOTO"
        state.error = "PRIVATE_MESSAGE"
        val error = IllegalStateException("PRIVATE_EXCEPTION https://private.invalid/token")
        assertSame(error, assertFailsWith<IllegalStateException> { act(GroupAction.JOIN_CONFIRMED) { throw error } })
        val all = logger.events.toString()
        listOf("PRIVATE", "987654", "https://", "private-group").forEach { assertFalse(all.contains(it), it) }
    }

    @Test fun `normal cancellation is an action and not an app error`() {
        state.selectedPhotos = listOf("photo")
        state.startUpload(); state.openSheet(GroupSheet.FAILED)
        act(GroupAction.SAVE_DISCARDED) { state.cancelSave() }
        act(GroupAction.PHOTO_DELETE_CANCELLED) { state.pendingDeleteId = null }
        assertEquals(2, logger.parameters(MapmoryAnalyticsEvent.GROUP_ACTION).size)
        assertTrue(logger.parameters(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
        assertFailsWith<CancellationException> { act(GroupAction.SAVED_ALBUM_OPENED) { throw CancellationException("private") } }
        assertTrue(logger.parameters(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
    }

    @Test fun `policy rejection has a stable reason and no app failure`() {
        val error = GroupRuleException(GroupRuleViolation.GroupFull)
        assertSame(error, assertFailsWith<GroupRuleException> { act(GroupAction.JOIN_CONFIRMED) { throw error } })
        assertEquals("groupfull", logger.parameters(MapmoryAnalyticsEvent.GROUP_ACTION_REJECTED).single()["reason"])
        assertTrue(logger.parameters(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
    }

    @Test fun `unavailable feature is rejected without fake completion`() {
        state.preview = false
        act(GroupAction.CREATE_SUBMITTED) { telemetry.blocked(state.analyticsContext()) }
        assertEquals("create_submitted", logger.parameters(MapmoryAnalyticsEvent.GROUP_ACTION_REJECTED).single()["action"])
        assertEquals("service_unavailable", logger.parameters(MapmoryAnalyticsEvent.GROUP_ACTION_REJECTED).single()["reason"])
        assertTrue(logger.parameters(MapmoryAnalyticsEvent.GROUP_PREVIEW_RESULT).isEmpty())
        assertTrue(logger.parameters(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
    }

    @Test fun `logger failure cannot block successful actions or mask the original exception`() {
        val throwing = GroupAnalytics(object : MapmoryAnalytics {
            override fun logEvent(name: String, parameters: Map<String, String>) { error("logger failed") }
            override fun setUserProperty(name: String, value: String?) = Unit
        })
        assertEquals(42, throwing.action(GroupAction.CREATE_OPENED, state::analyticsContext) { 42 })
        val original = IllegalStateException("original")
        assertSame(original, assertFailsWith<IllegalStateException> {
            throwing.action(GroupAction.CREATE_OPENED, state::analyticsContext) { throw original }
        })
    }

    @Test fun `views deduplicate recomposition but track different albums and overlays without IDs`() {
        telemetry.viewed(state.analyticsContext(), state.viewKey())
        repeat(5) { telemetry.viewed(state.analyticsContext(), state.viewKey()) }
        state.albumId = "PRIVATE_ALBUM_A"
        telemetry.viewed(state.analyticsContext(), state.viewKey())
        state.albumId = "PRIVATE_ALBUM_B"
        telemetry.viewed(state.analyticsContext(), state.viewKey())
        state.openSheet(GroupSheet.PHOTOS)
        telemetry.viewed(state.analyticsContext(), state.viewKey())
        assertEquals(4, logger.parameters(MapmoryAnalyticsEvent.GROUP_VIEWED).size)
        assertFalse(logger.events.toString().contains("PRIVATE"))
    }

    @Test fun `delete outcome distinguishes photo deletion record deletion and stale confirmation`() {
        state.pendingDeleteId = "1"
        act(GroupAction.PHOTO_DELETE_CONFIRMED) { state.deletePendingPhoto() }
        state.pendingDeleteId = "3"
        act(GroupAction.PHOTO_DELETE_CONFIRMED) { state.deletePendingPhoto() }
        act(GroupAction.PHOTO_DELETE_CONFIRMED) { state.deletePendingPhoto() } // stale duplicate has no success
        assertEquals(listOf("photo_deleted", "record_deleted"),
            logger.parameters(MapmoryAnalyticsEvent.GROUP_PREVIEW_RESULT).map { it["outcome"] })
    }

    @Test fun `events and parameters fit the Firebase custom event contract`() {
        act(GroupAction.CREATE_OPENED) { state.openSheet(GroupSheet.CREATE) }
        telemetry.viewed(state.analyticsContext(), state.viewKey())
        act(GroupAction.INVITE_GENERATED) {}
        assertFailsWith<IllegalStateException> { act(GroupAction.CREATE_SUBMITTED) { error("private") } }
        val namePattern = Regex("[A-Za-z][A-Za-z0-9_]{0,39}")
        logger.events.forEach { (name, parameters) ->
            assertTrue(namePattern.matches(name), name)
            assertTrue(parameters.size <= 25)
            parameters.forEach { (key, value) ->
                assertTrue(namePattern.matches(key), key)
                assertTrue(value.length <= 100, key)
            }
        }
    }

    @Test fun `restore reports malformed state without leaking the saved value`() {
        assertFails { GroupPreviewState.saver(logger).restore("PRIVATE_CORRUPTED_JSON") }
        assertEquals("group_state_restore", logger.parameters(MapmoryAnalyticsEvent.APP_ERROR).single()["operation"])
        assertFalse(logger.events.toString().contains("PRIVATE"))
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = with(GroupPreviewState.saver(logger)) { scope.save(state) }!!
        val restored = GroupPreviewState.saver(logger).restore(saved)!!
        assertEquals(state.groupId, restored.groupId)
    }

    @Test fun `gesture rollback origin is separate from user forward and resets even when handler throws`() {
        val registry = MapmoryBackHandlerRegistry()
        registry.register("entry", gestureForward = {
            assertTrue(registry.isRestoringGesture)
            error("handler")
        }) { false }
        assertFailsWith<IllegalStateException> { registry.handleGestureForward("entry", restoring = true) }
        assertFalse(registry.isRestoringGesture)
    }

    private class CapturingAnalytics : MapmoryAnalytics {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        override fun logEvent(name: String, parameters: Map<String, String>) { events += name to parameters }
        override fun setUserProperty(name: String, value: String?) = Unit
        fun parameters(event: String) = events.filter { it.first == event }.map { it.second }
    }
}
