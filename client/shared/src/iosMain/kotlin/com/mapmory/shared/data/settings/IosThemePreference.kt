package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

class IosThemePreference : ThemePreference {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun loadIsDarkTheme(): Boolean = preferences.boolForKey(IsDarkThemeKey)

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        preferences.setBool(isDarkTheme, forKey = IsDarkThemeKey)
    }

    override fun loadThemePalette(): MapmoryThemePalette =
        MapmoryThemePalette.fromStorageValue(preferences.stringForKey(ThemePaletteKey))

    override fun saveThemePalette(themePalette: MapmoryThemePalette) {
        preferences.setObject(themePalette.storageValue, forKey = ThemePaletteKey)
    }
}

private const val IsDarkThemeKey = "mapmory_is_dark_theme"
private const val ThemePaletteKey = "mapmory_theme_palette"
