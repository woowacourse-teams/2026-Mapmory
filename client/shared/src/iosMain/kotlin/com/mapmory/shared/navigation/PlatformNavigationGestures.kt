package com.mapmory.shared.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.UIKit.UIView

private object ScreenNavigationInfo : NavigationEventInfo()

@OptIn(InternalComposeUiApi::class, BetaInteropApi::class, ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformNavigationGestures(
    onBackStarted: () -> Boolean,
    onBackCancelled: () -> Unit,
    onForwardStarted: () -> Boolean,
    onForwardCancelled: () -> Unit,
) {
    val dispatcher = checkNotNull(LocalCompatNavigationEventDispatcherOwner.current)
        .navigationEventDispatcher
    val viewController = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val motion = LocalNavigationGestureMotion.current
    var preparation by remember { mutableStateOf<Job?>(null) }
    var destinationReady by remember { mutableStateOf(false) }
    var pendingProgress by remember { mutableStateOf(0f) }
    val progress = remember { Animatable(0f) }
    var direction by remember { mutableStateOf(0f) }
    var snapshotWidth by remember { mutableStateOf(0.0) }
    var snapshot by remember { mutableStateOf<UIView?>(null) }
    var navigationStarted by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var destinationSnapshot by remember { mutableStateOf<UIView?>(null) }
    var settlement by remember { mutableStateOf<Job?>(null) }
    val latestBackStarted by rememberUpdatedState(onBackStarted)
    val latestBackCancelled by rememberUpdatedState(onBackCancelled)
    val latestForwardStarted by rememberUpdatedState(onForwardStarted)
    val latestForwardCancelled by rememberUpdatedState(onForwardCancelled)

    fun removeSnapshot() {
        preparation?.cancel()
        preparation = null
        destinationReady = false
        pendingProgress = 0f
        motion.clearBackCover()
        motion.isActive = false
        destinationSnapshot?.removeFromSuperview()
        destinationSnapshot = null
        snapshot?.removeFromSuperview()
        snapshot = null
        snapshotWidth = 0.0
        direction = 0f
        navigationStarted = false
        settling = false
    }

    fun applyProgress(value: Float) {
        val offset = value.coerceIn(0f, 1f) * snapshotWidth
        if (direction < 0f) {
            destinationSnapshot?.setTransform(CGAffineTransformMakeTranslation(snapshotWidth - offset, 0.0))
        } else {
            motion.backProgress = value.coerceIn(0f, 1f)
        }
    }

    fun beginGesture(nextDirection: Float, navigate: () -> Boolean) {
        if (navigationStarted || settling) return
        removeSnapshot()
        if (nextDirection > 0f) {
            direction = nextDirection
            navigationStarted = true
            preparation = scope.launch {
                // Capture before changing either the entry or photo expansion state.
                motion.captureBackCover()
                motion.isActive = true
                if (!navigate()) {
                    removeSnapshot()
                    return@launch
                }
                progress.snapTo(0f)
                motion.awaitRenderedContent()
                destinationReady = true
                if (!settling) {
                    progress.snapTo(pendingProgress)
                    applyProgress(pendingProgress)
                }
            }
            return
        }
        val rootView = viewController.view
        val parent = rootView.superview ?: return
        val captured = rootView.snapshotViewAfterScreenUpdates(false) ?: return
        captured.userInteractionEnabled = false
        snapshotWidth = rootView.bounds.useContents { size.width }
        direction = nextDirection
        snapshot = captured
        // Forward keeps its UIKit covers without moving the recognizer.
        captured.setFrame(rootView.frame)
        parent.addSubview(captured)
        motion.isActive = true
        navigationStarted = navigate()
        if (!navigationStarted) removeSnapshot()
        if (navigationStarted) preparation = scope.launch {
            progress.snapTo(0f)
            motion.awaitRenderedContent()
            if (nextDirection < 0f) {
                // Only forward needs an incoming cover. For back, capturing here
                // can freeze the outgoing Metal frame over the live previous screen.
                val destination = rootView.snapshotViewAfterScreenUpdates(true)
                if (destination == null) {
                    latestForwardCancelled()
                    motion.awaitRenderedContent()
                    removeSnapshot()
                    return@launch
                }
                destination.userInteractionEnabled = false
                destination.setFrame(rootView.frame)
                destinationSnapshot = destination
                destination.setTransform(CGAffineTransformMakeTranslation(snapshotWidth, 0.0))
                parent.insertSubview(destination, aboveSubview = captured)
            }
            destinationReady = true
            if (!settling) {
                progress.snapTo(pendingProgress)
                applyProgress(pendingProgress)
            }
        }
    }

    fun updateGesture(event: NavigationEvent) {
        if (!navigationStarted || settling) return
        pendingProgress = event.progress.coerceIn(0f, 1f)
        // No animation or queued coroutine while dragging: a stationary finger
        // must leave the covering screen at exactly the same position.
        if (destinationReady) applyProgress(pendingProgress)
    }

    fun cancelGesture(restore: () -> Unit) {
        if (!navigationStarted || settling) return
        settling = true
        settlement = scope.launch {
            preparation?.join()
            if (!navigationStarted) return@launch
            progress.snapTo(pendingProgress)
            progress.animateTo(0f, tween(durationMillis = 180)) { applyProgress(value) }
            // Restore under the fully covering snapshot (back) or offscreen destination
            // (forward), so the live screen never changes midway through the gesture.
            restore()
            motion.awaitRenderedContent()
            removeSnapshot()
        }
    }

    fun completeGesture() {
        if (!navigationStarted || settling) return
        settling = true
        settlement = scope.launch {
            preparation?.join()
            if (!navigationStarted) return@launch
            progress.snapTo(pendingProgress)
            progress.animateTo(1f, tween(durationMillis = 140)) { applyProgress(value) }
            removeSnapshot()
            progress.snapTo(0f)
        }
    }

    val handler = remember(dispatcher, viewController) {
        object : NavigationEventHandler<NavigationEventInfo>(
            initialInfo = ScreenNavigationInfo,
            isBackEnabled = true,
            isForwardEnabled = true,
        ) {
            override fun onBackStarted(event: NavigationEvent) {
                beginGesture(nextDirection = 1f) { latestBackStarted() }
            }

            override fun onBackProgressed(event: NavigationEvent) = updateGesture(event)

            override fun onBackCancelled() = cancelGesture { latestBackCancelled() }

            override fun onBackCompleted() = completeGesture()

            override fun onForwardStarted(event: NavigationEvent) {
                beginGesture(nextDirection = -1f) { latestForwardStarted() }
            }

            override fun onForwardProgressed(event: NavigationEvent) = updateGesture(event)

            override fun onForwardCancelled() = cancelGesture { latestForwardCancelled() }

            override fun onForwardCompleted() = completeGesture()
        }
    }
    DisposableEffect(dispatcher, handler) {
        dispatcher.addHandler(handler)
        onDispose {
            handler.remove()
            settlement?.cancel()
            removeSnapshot()
        }
    }
}
