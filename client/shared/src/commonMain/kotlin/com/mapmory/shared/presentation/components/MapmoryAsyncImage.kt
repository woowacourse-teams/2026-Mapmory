package com.mapmory.shared.presentation.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.compose.AsyncImage
import com.mapmory.shared.presentation.triprecord.screen.TripPhotoPlaceholder

internal data class MapmoryImageTransitionScope(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val painters: SnapshotStateMap<String, Painter>,
) {
    val isTransitionActive: Boolean
        get() = sharedScope.isTransitionActive ||
            visibilityScope.transition.currentState != visibilityScope.transition.targetState
}

internal val LocalMapmoryImageTransitionScope =
    staticCompositionLocalOf<MapmoryImageTransitionScope?> { null }

@Composable
internal fun MapmoryAsyncImage(
    imageBytes: ByteArray?,
    imageUri: String? = null,
    fallbackBytes: ByteArray? = null,
    fallbackUri: String? = null,
    cacheKey: String? = null,
    blackLoadingBackground: Boolean = false,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    contentScale: ContentScale = ContentScale.Crop,
    sharedImageKey: String? = null,
) {
    val transition = LocalMapmoryImageTransitionScope.current
    var lastPainter by remember(sharedImageKey, contentDescription) {
        mutableStateOf(sharedImageKey?.let { transition?.painters?.get(it) })
    }
    var useFallback by remember(imageBytes, imageUri, fallbackBytes, fallbackUri) { mutableStateOf(false) }
    var showPlaceholder by remember(imageBytes, imageUri, fallbackBytes, fallbackUri) { mutableStateOf(true) }
    val primaryModel: Any? = imageUri ?: imageBytes
    val fallbackModel: Any? = fallbackUri ?: fallbackBytes ?: imageBytes
    val model = if (useFallback || primaryModel == null) fallbackModel else primaryModel
    val context = LocalPlatformContext.current
    val request = remember(context, model, cacheKey, useFallback, blackLoadingBackground) {
        ImageRequest.Builder(context).data(model).apply {
            crossfade(false)
            cacheKey?.let { key ->
                memoryCacheKey("$key:$useFallback")
                diskCacheKey("$key:$useFallback")
            }
        }.build()
    }

    // Keep the fullscreen backdrop outside the shared element. Only the photo's
    // fitted rectangle moves, and Crop reveals its edges as that rectangle grows.
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val intrinsicSize = lastPainter?.intrinsicSize
        val ratio = intrinsicSize?.let { size ->
            (size.width / size.height).takeIf { it.isFinite() && it > 0f }
        }
        val photoSize = if (contentScale == ContentScale.Fit && ratio != null) {
            val width = minOf(maxWidth, maxHeight * ratio)
            Modifier.size(width, width / ratio)
        } else Modifier.fillMaxSize()
        val imageModifier = if (transition != null && sharedImageKey != null) {
            with(transition.sharedScope) {
                photoSize.sharedElement(
                    sharedContentState = rememberSharedContentState(sharedImageKey),
                    animatedVisibilityScope = transition.visibilityScope,
                    boundsTransform = { _, _ -> tween(320, easing = FastOutSlowInEasing) },
                )
            }
        } else photoSize
        val photoContentScale = if (ratio != null && contentScale == ContentScale.Fit) {
            ContentScale.Crop
        } else contentScale
        if (model == null) {
            if (blackLoadingBackground) {
                Box(imageModifier.clip(shape).background(Color.Black))
            } else {
                TripPhotoPlaceholder(imageModifier, shape)
            }
        } else {
            Box(modifier = imageModifier.clip(shape).then(
                if (blackLoadingBackground) Modifier.background(Color.Black) else Modifier,
            )) {
                val retainedPainter = lastPainter
                if (showPlaceholder && retainedPainter != null) {
                    Image(
                        painter = retainedPainter,
                        contentDescription = null,
                        contentScale = photoContentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (showPlaceholder && !blackLoadingBackground) {
                    TripPhotoPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        shape = shape,
                    )
                }
                AsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    contentScale = photoContentScale,
                    modifier = Modifier.fillMaxSize(),
                    onLoading = { showPlaceholder = true },
                    onSuccess = { state ->
                        lastPainter = state.painter
                        sharedImageKey?.let { transition?.painters?.set(it, state.painter) }
                        showPlaceholder = false
                    },
                    onError = {
                        showPlaceholder = true
                        if (!useFallback && primaryModel != null && fallbackModel != null && fallbackModel != primaryModel) {
                            useFallback = true
                        }
                    },
                )
            }
        }
    }
}
