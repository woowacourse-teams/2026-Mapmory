package com.mapmory.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred

internal class NavigationGestureMotion {
    var isActive by androidx.compose.runtime.mutableStateOf(false)
    var composedEntryId by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var captureRequest by androidx.compose.runtime.mutableStateOf<CompletableDeferred<GraphicsLayer>?>(null)
    private var backCover by androidx.compose.runtime.mutableStateOf<ImageBitmap?>(null)
    var backProgress by androidx.compose.runtime.mutableFloatStateOf(0f)

    suspend fun captureBackCover() {
        val request = CompletableDeferred<GraphicsLayer>()
        captureRequest = request
        try {
            val layer = request.await()
            backCover = layer.toImageBitmap()
            backProgress = 0f
        } finally {
            captureRequest = null
        }
    }

    fun clearBackCover() {
        captureRequest?.cancel()
        captureRequest = null
        backCover = null
        backProgress = 0f
    }

    private var renderRequest by mutableIntStateOf(0)
    private var appliedRequest = 0
    private var rendered: CompletableDeferred<Unit>? = null

    // A frame-clock tick alone does not mean the restored NavHost has been drawn.
    suspend fun awaitRenderedContent() {
        val completion = CompletableDeferred<Unit>()
        rendered?.cancel()
        rendered = completion
        renderRequest++
        try {
            completion.await()
            // Allow the draw that acknowledged this request to be presented by UIKit.
            withFrameNanos { }
        } finally {
            if (rendered === completion) rendered = null
        }
    }

    @Composable
    fun renderObserver(expectedEntryId: () -> String?): Modifier {
        val captureLayer = rememberGraphicsLayer()
        val request = renderRequest
        SideEffect { appliedRequest = request }
        return Modifier.drawWithContent {
            val capture = captureRequest
            if (capture != null) {
                // Freeze the outgoing content before navigation, in the same renderer.
                if (!capture.isCompleted) {
                    captureLayer.record { this@drawWithContent.drawContent() }
                    capture.complete(captureLayer)
                }
                drawLayer(captureLayer)
            } else {
                drawContent()
            }
            backCover?.let { cover ->
                translate(left = backProgress * size.width) {
                    drawImage(cover, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
            }
            if (appliedRequest == renderRequest && request == renderRequest &&
                composedEntryId == expectedEntryId()
            ) {
                rendered?.complete(Unit)
            }
        }
    }
}

internal val LocalNavigationGestureMotion =
    androidx.compose.runtime.staticCompositionLocalOf { NavigationGestureMotion() }
