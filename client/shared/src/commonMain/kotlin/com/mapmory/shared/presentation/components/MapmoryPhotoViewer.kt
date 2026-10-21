package com.mapmory.shared.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

@Composable
internal fun <T> MapmoryPhotoExpansion(
    photo: T?,
    content: @Composable (T?) -> Unit,
) {
    val gestureMotion = com.mapmory.shared.navigation.LocalNavigationGestureMotion.current
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        val painters = remember { mutableMapOf<String, Painter>() }
        AnimatedContent(
            targetState = photo,
            contentKey = { it != null },
            transitionSpec = {
                if (gestureMotion.isActive) EnterTransition.None togetherWith ExitTransition.None
                else photoExpansionTransition(isOpening = targetState != null)
            },
            label = "photo-expansion",
        ) { targetPhoto ->
            CompositionLocalProvider(
                LocalMapmoryImageTransitionScope provides if (gestureMotion.isActive) null else MapmoryImageTransitionScope(sharedScope, this, painters),
            ) { content(targetPhoto) }
        }
    }
}

@Composable
internal fun MapmoryPhotoViewer(
    title: String,
    onClose: () -> Unit,
    closeContentDescription: String,
    canDragDown: () -> Boolean = { true },
    showTopBar: Boolean = true,
    trailingContent: @Composable () -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var returnJob by remember { mutableStateOf<Job?>(null) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestClose by rememberUpdatedState(onClose)
    val latestCanDrag by rememberUpdatedState(canDragDown)
    val dismissDistance = with(LocalDensity.current) { 120.dp.toPx() }
    Box(Modifier.fillMaxSize().then(photoViewerBackground())) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().clipToBounds()) {
            Box(
                Modifier.fillMaxSize()
                    .photoDragDown(
                        canStart = { !closing && latestCanDrag() },
                        onStart = { returnJob?.cancel() },
                        onDrag = { delta -> dragOffset = (dragOffset + delta).coerceAtLeast(0f) },
                        onFinish = { cancelled ->
                            if (!cancelled && dragOffset >= dismissDistance) {
                                closing = true
                                latestClose()
                            } else {
                                returnJob = scope.launch {
                                    animate(dragOffset, 0f, animationSpec = tween(180)) { value, _ ->
                                        dragOffset = value
                                    }
                                }
                            }
                        },
                    )
                    .offset { IntOffset(0, dragOffset.roundToInt()) },
            ) { content() }
        }
        AnimatedVisibility(
            visible = showTopBar,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .height(56.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MapmoryBackButton(
                    onClick = onClose,
                    color = Color.White,
                    label = closeContentDescription,
                )
                Text(
                    title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                trailingContent()
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
        ) { bottomContent() }
    }
}
