package com.mapmory.shared

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class MapmoryThemeState(
    val isDark: Boolean,
    val onThemeChange: (Boolean) -> Unit,
)

val LocalMapmoryTheme = staticCompositionLocalOf {
    MapmoryThemeState(
        isDark = false,
        onThemeChange = {},
    )
}
