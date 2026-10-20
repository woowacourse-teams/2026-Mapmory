package com.mapmory.shared.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class PhotoDragDownTest {
    @Test
    fun `downward drag follows movement and release finishes once`() = fixture {
        press(100f, 100f)
        move(100f, 150f)
        assertEquals(1, starts)
        val first = distance
        move(100f, 190f)
        assertEquals(first + 40f, distance)
        move(100f, 170f)
        assertEquals(first + 20f, distance)
        release(100f, 170f)
        assertEquals(listOf(false), finishes)
    }

    @Test
    fun `horizontal paging upward drag and tap do not start dismissal`() = fixture {
        press(100f, 100f)
        move(160f, 103f)
        move(160f, 190f)
        release(160f, 190f)
        press(100f, 100f)
        move(100f, 40f)
        release(100f, 40f)
        press(100f, 100f)
        release(100f, 100f)
        assertEquals(0, starts)
        assertTrue(finishes.isEmpty())
    }

    @Test
    fun `zoom blocks starting and cancels an active drag`() = fixture {
        enabled = false
        press(100f, 100f)
        move(100f, 190f)
        release(100f, 190f)
        assertEquals(0, starts)
        enabled = true
        press(100f, 100f)
        move(100f, 150f)
        enabled = false
        move(100f, 190f)
        release(100f, 190f)
        assertEquals(listOf(true), finishes)
    }

    private fun fixture(test: Fixture.() -> Unit) {
        val fixture = Fixture()
        try { fixture.test() } finally { fixture.scene.close() }
    }

    private class Fixture {
        var enabled = true
        var starts = 0
        var distance = 0f
        val finishes = mutableListOf<Boolean>()
        val scene = ImageComposeScene(400, 800) {
            Box(Modifier.fillMaxSize().photoDragDown(
                canStart = { enabled },
                onStart = { starts++ },
                onDrag = { distance += it },
                onFinish = { finishes += it },
            ))
        }.also { it.render().close() }
        private var time = 0L
        fun press(x: Float, y: Float) = send(PointerEventType.Press, x, y)
        fun move(x: Float, y: Float) = send(PointerEventType.Move, x, y)
        fun release(x: Float, y: Float) = send(PointerEventType.Release, x, y)
        private fun send(type: PointerEventType, x: Float, y: Float) {
            scene.sendPointerEvent(type, Offset(x, y), timeMillis = ++time * 16, type = PointerType.Touch)
        }
    }
}
