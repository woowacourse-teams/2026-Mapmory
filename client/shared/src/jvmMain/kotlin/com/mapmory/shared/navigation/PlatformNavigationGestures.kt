package com.mapmory.shared.navigation

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformNavigationGestures(
    onBackStarted: () -> Boolean,
    onBackCancelled: () -> Unit,
    onForwardStarted: () -> Boolean,
    onForwardCancelled: () -> Unit,
) = Unit
