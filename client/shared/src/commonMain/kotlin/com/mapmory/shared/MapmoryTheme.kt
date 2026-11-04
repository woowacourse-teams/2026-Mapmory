package com.mapmory.shared

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
enum class MapmoryColorTheme {
    GREEN,
    BLUE,
}

@Immutable
data class MapmoryThemeState(
    val isDark: Boolean,
    val colorTheme: MapmoryColorTheme = MapmoryColorTheme.GREEN,
    val onColorThemeChange: (MapmoryColorTheme) -> Unit = {},
    val onThemeChange: (Boolean) -> Unit,
)

val LocalMapmoryTheme = staticCompositionLocalOf {
    MapmoryThemeState(
        isDark = false,
        onThemeChange = {},
    )
}
