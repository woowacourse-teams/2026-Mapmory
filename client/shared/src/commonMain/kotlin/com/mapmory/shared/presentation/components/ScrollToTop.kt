package com.mapmory.shared.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ScrollToTopRegistry {
    class Target(val isActive: () -> Boolean, val scroll: () -> Unit)
    private val targets = mutableListOf<Target>()
    fun register(target: Target) { targets += target }
    fun unregister(target: Target) { targets -= target }
    fun scrollToTop() { targets.lastOrNull { it.isActive() }?.scroll?.invoke() }
}

private val LocalScrollToTopRegistry = staticCompositionLocalOf<ScrollToTopRegistry?> { null }

@Composable
internal fun ProvideScrollToTop(content: @Composable () -> Unit) {
    val registry = remember { ScrollToTopRegistry() }
    PlatformStatusBarTap { registry.scrollToTop() }
    CompositionLocalProvider(LocalScrollToTopRegistry provides registry, content = content)
}

@Composable
internal fun ScrollToTopEffect(state: ScrollState, enabled: Boolean = true) {
    ScrollToTopEffect(enabled) { state.animateScrollTo(0) }
}

@Composable
internal fun ScrollToTopEffect(state: LazyListState, enabled: Boolean = true) {
    ScrollToTopEffect(enabled) { state.animateScrollToItem(0) }
}

@Composable
internal fun ScrollToTopEffect(state: LazyGridState, enabled: Boolean = true) {
    ScrollToTopEffect(enabled) { state.animateScrollToItem(0) }
}

@Composable
private fun ScrollToTopEffect(enabled: Boolean, scroll: suspend () -> Unit) {
    val registry = LocalScrollToTopRegistry.current ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val latestEnabled by rememberUpdatedState(enabled)
    val latestScroll by rememberUpdatedState(scroll)
    DisposableEffect(registry, lifecycle) {
        var scrollJob: Job? = null
        val target = ScrollToTopRegistry.Target(
            isActive = { latestEnabled && lifecycle.currentState == Lifecycle.State.RESUMED },
            scroll = {
                scrollJob?.cancel()
                scrollJob = scope.launch { latestScroll() }
            },
        )
        registry.register(target)
        onDispose {
            registry.unregister(target)
            scrollJob?.cancel()
        }
    }
}

@Composable
internal expect fun PlatformStatusBarTap(onTap: () -> Unit)
