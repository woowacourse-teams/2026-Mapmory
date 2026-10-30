package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

class IosThemePreference : ThemePreference {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun loadIsDarkTheme(): Boolean = preferences.boolForKey(IsDarkThemeKey)

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        preferences.setBool(isDarkTheme, forKey = IsDarkThemeKey)
    }
}

private const val IsDarkThemeKey = "mapmory_is_dark_theme"
