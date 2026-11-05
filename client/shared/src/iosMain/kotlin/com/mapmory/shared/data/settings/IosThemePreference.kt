package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

class IosThemePreference : ThemePreference {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun loadIsDarkTheme(): Boolean = preferences.boolForKey(IsDarkThemeKey)

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        preferences.setBool(isDarkTheme, forKey = IsDarkThemeKey)
    }

    override fun loadThemePalette(): MapmoryThemePalette {
        val stored = preferences.stringForKey(ThemePaletteKey)
        if (stored != null) return MapmoryThemePalette.fromStorageValue(stored)
        return legacyThemePalette(preferences.stringForKey(LegacyColorThemeKey))?.also(::saveThemePalette)
            ?: MapmoryThemePalette.MAPMORY_GREEN
    }

    override fun saveThemePalette(themePalette: MapmoryThemePalette) {
        preferences.setObject(themePalette.storageValue, forKey = ThemePaletteKey)
    }
}

private const val IsDarkThemeKey = "mapmory_is_dark_theme"
private const val ThemePaletteKey = "mapmory_theme_palette"
private const val LegacyColorThemeKey = "mapmory_color_theme"
