package com.mapmory.shared.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class NavigationBackRenderingTest {
    @Test
    fun `back exposes previous content while outgoing cover follows and reverses drag`() {
        val motion = NavigationGestureMotion()
        val screen = mutableStateOf(Color.Red)
        val scene = ImageComposeScene(width = 100, height = 40) {
            Box(Modifier.fillMaxSize().then(motion.renderObserver { null })) {
                Box(Modifier.fillMaxSize().background(screen.value))
            }
        }
        var frame = 0L
        fun render() = scene.render(++frame * 16_666_667L).use {
            it.toComposeImageBitmap().toPixelMap()
        }
        try {
            render()
            val capture = CoroutineScope(Dispatchers.Unconfined).launch { motion.captureBackCover() }
            repeat(3) { render() }
            assertTrue(capture.isCompleted, "Outgoing cover must be captured before navigation")
            screen.value = Color.Blue
            motion.backProgress = 0.5f
            repeat(2) { render() }
            val halfway = render()
            assertEquals(Color.Blue, halfway[20, 20], "Revealed area is the previous screen")
            assertEquals(Color.Red, halfway[80, 20], "Moving cover is the outgoing screen")
            val paused = render()
            assertEquals(halfway[20, 20], paused[20, 20])
            assertEquals(halfway[80, 20], paused[80, 20])

            motion.backProgress = 0.2f
            val reversed = render()
            assertEquals(Color.Blue, reversed[10, 20])
            assertEquals(Color.Red, reversed[30, 20])

            motion.backProgress = 0f
            assertEquals(Color.Red, render()[20, 20], "Cancelled drag fully covers previous content")
            screen.value = Color.Red
            render()
            motion.clearBackCover()
            assertEquals(Color.Red, render()[20, 20], "Cancellation never exposes previous content")

            val secondCapture = CoroutineScope(Dispatchers.Unconfined).launch { motion.captureBackCover() }
            repeat(3) { render() }
            assertTrue(secondCapture.isCompleted)
            screen.value = Color.Blue
            motion.backProgress = 1f
            assertEquals(Color.Blue, render()[80, 20])
            motion.clearBackCover()
            assertEquals(Color.Blue, render()[80, 20], "Removing cover must not flash outgoing content")
        } finally {
            scene.close()
        }
    }
}
