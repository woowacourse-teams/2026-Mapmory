package com.mapmory.shared.presentation.group

import androidx.compose.runtime.CompositionLocalProvider
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.analytics.RecordingAnalytics
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class GroupFlowTest {
    @Test
    fun `social names do not merge different members`() {
        val album = GroupAlbum("a", "여행", "제주", "오늘", listOf(
            GroupPhoto("1", 1, "member-a", "김민수"),
            GroupPhoto("2", 1, "member-a", "김민수"),
            GroupPhoto("3", 2, "member-b", "김민수"),
        ))
        assertEquals(listOf("김민수", "김민수"), album.contributors)
    }

    @Test
    fun `group internal back and cancelled gestures restore the actual composed screen`() {
        val registry = MapmoryBackHandlerRegistry()
        val analytics = RecordingAnalytics()
        val scene = ImageComposeScene(412, 892) {
            CompositionLocalProvider(LocalMapmoryAnalytics provides analytics) {
                GroupRoute({}, {}, {}, {}, true, registry, "group")
            }
        }
        var frame = 0L
        fun render() { repeat(60) { scene.render(++frame * 16_666_667L).close() } }
        fun tap(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), type = PointerType.Touch)
            render()
        }
        try {
            render()
            assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.GROUP_VIEWED })
            render()
            assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.GROUP_VIEWED }, "Rendering does not duplicate views")
            assertFalse(registry.handleGestureBack("group"))
            tap(90f, 24f) // Enable the explicitly labelled UI preview.
            tap(180f, 250f) // Open the sample group card.
            // Scroll home before opening its album; compare real rendered pixels on return.
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(220f, 600f), scrollDelta = Offset(0f, 120f))
            render()
            fun pixels(): ByteArray {
                scene.sendPointerEvent(PointerEventType.Move, Offset(-10f, -10f), type = PointerType.Mouse)
                scene.sendPointerEvent(PointerEventType.Exit, Offset(-10f, -10f), type = PointerType.Mouse)
                render()
                return scene.render(++frame * 16_666_667L).use { it.encodeToData()!!.bytes }
            }
            val homeBefore = pixels()
            tap(180f, 350f)
            assertTrue(registry.handleGestureBack("group"), "Album must consume edge back")
            render()
            assertTrue(homeBefore.contentEquals(pixels()), "Returning from album must retain home scroll position")
            assertTrue(registry.handleGestureForward("group", restoring = true), "Cancelled gesture restores album")
            render()
            val albumBefore = pixels()
            java.io.File("build/reports/group-album-preview.png").apply { parentFile.mkdirs(); writeBytes(albumBefore) }
            tap(90f, 530f) // First photo below the shared journal cover and contributor heading.
            assertTrue(registry.handleGestureBack("group"), "Photo viewer must consume edge back")
            render()
            assertTrue(albumBefore.contentEquals(pixels()), "Closing photo restores the album screen")
            assertTrue(registry.handleBack("group"))
            render()
            assertTrue(registry.handleGestureBack("group"), "Group home must consume edge back")
            render()
            assertTrue(registry.handleGestureForward("group", restoring = true), "Cancelled edge back must restore home")
            render()
            assertTrue(registry.handleBack("group"))
            render()
            assertFalse(registry.handleBack("group"), "Back from group home reaches group list")
            assertFalse(registry.handleGestureForward("group"), "Normal back clears gesture forward history")
            val actions = analytics.events.filter { it.first == MapmoryAnalyticsEvent.GROUP_ACTION }.map { it.second["action"] }
            listOf("preview_toggled", "group_opened", "album_opened", "photo_opened", "edge_back", "edge_back_cancelled", "system_back").forEach {
                assertTrue(it in actions, "Missing actual callback event: $it")
            }
            assertTrue(analytics.events.none { it.first == MapmoryAnalyticsEvent.APP_ERROR })
            assertTrue(analytics.events.none { it.second.values.any { value -> value.contains("preview-group") || value == "성우" } })
        } finally { scene.close() }
    }
}
