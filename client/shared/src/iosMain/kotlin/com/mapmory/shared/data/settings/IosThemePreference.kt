package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults
import com.mapmory.shared.MapmoryColorTheme

class IosThemePreference : ThemePreference {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun loadIsDarkTheme(): Boolean = preferences.boolForKey(IsDarkThemeKey)

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        preferences.setBool(isDarkTheme, forKey = IsDarkThemeKey)
    }

    override fun loadColorTheme(): MapmoryColorTheme = preferences.stringForKey(ColorThemeKey)
        ?.let { value -> MapmoryColorTheme.values().firstOrNull { it.name == value } }
        ?: MapmoryColorTheme.GREEN

    override fun saveColorTheme(colorTheme: MapmoryColorTheme) {
        preferences.setObject(colorTheme.name, forKey = ColorThemeKey)
    }
}

private const val IsDarkThemeKey = "mapmory_is_dark_theme"
private const val ColorThemeKey = "mapmory_color_theme"
