package com.mapmory.shared.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class PhotoViewerFrameRetentionTest {
    @Test
    fun `moving between current and adjacent page keeps the last displayed photo`() {
        val isCurrent = mutableStateOf(true)
        val scene = ImageComposeScene(100, 100) {
            SharedTransitionLayout {
                val sharedScope = this
                AnimatedVisibility(true) {
                    val painters = mutableMapOf<String, Painter>("photo" to ColorPainter(Color.Red))
                    CompositionLocalProvider(
                        LocalMapmoryImageTransitionScope provides
                            MapmoryImageTransitionScope(sharedScope, this, painters),
                    ) {
                        MapmoryAsyncImage(
                            imageBytes = null,
                            contentDescription = "photo",
                            sharedImageKey = "photo".takeIf { isCurrent.value },
                            imageIdentity = "photo",
                            blackLoadingBackground = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        var frame = 0L
        fun center() = scene.render(++frame * 16_666_667L).use {
            it.toComposeImageBitmap().toPixelMap()[50, 50]
        }
        try {
            repeat(3) { center() }
            assertEquals(Color.Red, center())
            repeat(4) {
                isCurrent.value = !isCurrent.value
                repeat(3) { center() }
                assertEquals(Color.Red, center(), "Page role changes must not flash a placeholder")
            }
        } finally { scene.close() }
    }
}
