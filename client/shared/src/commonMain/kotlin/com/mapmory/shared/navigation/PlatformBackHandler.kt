package com.mapmory.shared.navigation

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
