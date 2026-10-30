package com.mapmory.shared.navigation

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlatformNavigationGestures(
    onBackStarted: () -> Boolean,
    onBackCancelled: () -> Unit,
    onForwardStarted: () -> Boolean,
    onForwardCancelled: () -> Unit,
)
