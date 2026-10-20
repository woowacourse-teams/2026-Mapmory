package com.mapmory.shared.presentation.components

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import coil3.compose.AsyncImage
import com.mapmory.shared.presentation.triprecord.screen.TripPhotoPlaceholder

internal data class MapmoryImageTransitionScope(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val painters: MutableMap<String, Painter>,
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
    decodeOriginalSize: Boolean = false,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    contentScale: ContentScale = ContentScale.Crop,
    sharedImageKey: String? = null,
    imageIdentity: String? = sharedImageKey,
    sharedCornerRadius: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    onReadyChanged: (Boolean) -> Unit = {},
) {
    val transition = LocalMapmoryImageTransitionScope.current
    // Both incoming and outgoing photos follow the same timeline as their bounds.
    // The fullscreen photo starts rounded instead of switching to a rectangle on entry.
    val animatedShape = if (transition != null && sharedImageKey != null) {
        val cornerRadius by transition.visibilityScope.transition.animateDp(
            transitionSpec = { tween(200, easing = FastOutSlowInEasing) },
            label = "photo-corner-radius",
        ) { state ->
            val isThumbnail = contentScale != ContentScale.Fit
            if ((state == EnterExitState.Visible) == isThumbnail) sharedCornerRadius else 0.dp
        }
        RoundedCornerShape(cornerRadius)
    } else shape
    // Becoming the current pager page changes sharedImageKey, not image identity.
    // Keep the displayed frame while preview/original requests are replaced.
    var lastPainter by remember(imageIdentity, contentDescription) {
        mutableStateOf(imageIdentity?.let { transition?.painters?.get(it) })
    }
    var useFallback by remember(imageBytes, imageUri, fallbackBytes, fallbackUri) { mutableStateOf(false) }
    var showPlaceholder by remember(imageBytes, imageUri, fallbackBytes, fallbackUri) { mutableStateOf(true) }
    val primaryModel: Any? = imageUri ?: imageBytes
    val fallbackModel: Any? = fallbackUri ?: fallbackBytes ?: imageBytes
    val model = if (useFallback || primaryModel == null) fallbackModel else primaryModel
    val animateRetainedPhoto = lastPainter != null && transition?.isTransitionActive == true
    var imageLoaded by remember(model, cacheKey, animateRetainedPhoto) { mutableStateOf(false) }
    val isReady = imageLoaded || animateRetainedPhoto
    val latestOnReadyChanged by rememberUpdatedState(onReadyChanged)
    LaunchedEffect(isReady) { latestOnReadyChanged(isReady) }
    val context = LocalPlatformContext.current
    val request = remember(context, model, cacheKey, useFallback, blackLoadingBackground, decodeOriginalSize) {
        ImageRequest.Builder(context).data(model).apply {
            crossfade(false)
            if (decodeOriginalSize) size(Size.ORIGINAL)
            cacheKey?.let { key ->
                memoryCacheKey("$key:$useFallback:$decodeOriginalSize")
                diskCacheKey("$key:$useFallback:$decodeOriginalSize")
            }
        }.build()
    }

    // Keep the fullscreen backdrop outside the shared element. Only the photo's
    // fitted rectangle moves, and Crop reveals its edges as that rectangle grows.
    BoxWithConstraints(
        modifier.then(if (onClick != null) Modifier.clickable(enabled = isReady, role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
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
                    boundsTransform = { _, _ -> tween(200, easing = FastOutSlowInEasing) },
                )
            }
        } else photoSize
        val photoContentScale = if (ratio != null && contentScale == ContentScale.Fit) {
            ContentScale.Crop
        } else contentScale
        if (model == null) {
            val retainedPainter = lastPainter
            if (retainedPainter != null) {
                Image(
                    painter = retainedPainter,
                    contentDescription = contentDescription,
                    contentScale = photoContentScale,
                    modifier = imageModifier.clip(animatedShape),
                )
            } else if (blackLoadingBackground) {
                Box(imageModifier.clip(animatedShape).background(Color.Black))
            } else {
                TripPhotoPlaceholder(imageModifier, animatedShape)
            }
        } else {
            Box(modifier = imageModifier.clip(animatedShape).then(
                if (blackLoadingBackground) Modifier.background(Color.Black) else Modifier,
            )) {
                val retainedPainter = lastPainter
                // Keep the last decoded frame underneath Coil during preview/full-size swaps.
                // Loading callbacks can precede the first rendered frame on iOS.
                if (retainedPainter != null && (!imageLoaded || animateRetainedPhoto)) {
                    Image(
                        painter = retainedPainter,
                        contentDescription = null,
                        contentScale = photoContentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (showPlaceholder && !blackLoadingBackground) {
                    TripPhotoPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        shape = animatedShape,
                    )
                }
                if (!animateRetainedPhoto) AsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    contentScale = photoContentScale,
                    modifier = Modifier.fillMaxSize(),
                    onLoading = { showPlaceholder = true; imageLoaded = false },
                    onSuccess = { state ->
                        lastPainter = state.painter
                        // The grid's shared cache must not retain every decoded original.
                        if (!decodeOriginalSize) {
                            sharedImageKey?.let { transition?.painters?.set(it, state.painter) }
                        }
                        showPlaceholder = false
                        imageLoaded = true
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
