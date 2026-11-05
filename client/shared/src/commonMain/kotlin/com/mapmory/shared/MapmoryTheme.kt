package com.mapmory.shared

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.mapmory.shared.data.settings.MapmoryThemePalette

@Immutable
data class MapmoryThemeState(
    val isDark: Boolean,
    val palette: MapmoryThemePalette,
    val onThemeChange: (Boolean) -> Unit,
    val onPaletteChange: (MapmoryThemePalette) -> Unit,
)

val LocalMapmoryTheme = staticCompositionLocalOf {
    MapmoryThemeState(
        isDark = false,
        palette = MapmoryThemePalette.MAPMORY_GREEN,
        onThemeChange = {},
        onPaletteChange = {},
    )
}
