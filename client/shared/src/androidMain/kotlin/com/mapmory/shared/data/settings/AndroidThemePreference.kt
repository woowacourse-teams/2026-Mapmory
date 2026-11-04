package com.mapmory.shared.data.settings

import android.content.Context
import com.mapmory.shared.MapmoryColorTheme

class AndroidThemePreference(context: Context) : ThemePreference {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    override fun loadIsDarkTheme(): Boolean = preferences.getBoolean(IsDarkThemeKey, false)

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        preferences.edit().putBoolean(IsDarkThemeKey, isDarkTheme).apply()
    }

    override fun loadColorTheme(): MapmoryColorTheme = preferences.getString(ColorThemeKey, null)
        ?.let { value -> MapmoryColorTheme.values().firstOrNull { it.name == value } }
        ?: MapmoryColorTheme.GREEN

    override fun saveColorTheme(colorTheme: MapmoryColorTheme) {
        preferences.edit().putString(ColorThemeKey, colorTheme.name).apply()
    }
}

private const val PreferencesName = "mapmory_settings"
private const val IsDarkThemeKey = "is_dark_theme"
private const val ColorThemeKey = "color_theme"
