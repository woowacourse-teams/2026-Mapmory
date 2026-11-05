package com.mapmory.shared.data.settings

import android.content.Context

class AndroidThemePreference(context: Context) : ThemePreference {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    override fun loadIsDarkTheme(): Boolean = preferences.getBoolean(IsDarkThemeKey, false)

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        preferences.edit().putBoolean(IsDarkThemeKey, isDarkTheme).apply()
    }

    override fun loadThemePalette(): MapmoryThemePalette =
        MapmoryThemePalette.fromStorageValue(preferences.getString(ThemePaletteKey, null))

    override fun saveThemePalette(themePalette: MapmoryThemePalette) {
        preferences.edit().putString(ThemePaletteKey, themePalette.storageValue).apply()
    }
}

private const val PreferencesName = "mapmory_settings"
private const val IsDarkThemeKey = "is_dark_theme"
private const val ThemePaletteKey = "theme_palette"
