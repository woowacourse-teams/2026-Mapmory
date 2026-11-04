package com.mapmory.shared.data.settings

import com.mapmory.shared.MapmoryColorTheme

interface ThemePreference {
    fun loadIsDarkTheme(): Boolean

    fun saveIsDarkTheme(isDarkTheme: Boolean)

    fun loadColorTheme(): MapmoryColorTheme

    fun saveColorTheme(colorTheme: MapmoryColorTheme)
}

class MemoryThemePreference(
    initialIsDarkTheme: Boolean = false,
    initialColorTheme: MapmoryColorTheme = MapmoryColorTheme.GREEN,
) : ThemePreference {
    private var isDarkTheme = initialIsDarkTheme
    private var colorTheme = initialColorTheme

    override fun loadIsDarkTheme(): Boolean = isDarkTheme

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        this.isDarkTheme = isDarkTheme
    }

    override fun loadColorTheme(): MapmoryColorTheme = colorTheme

    override fun saveColorTheme(colorTheme: MapmoryColorTheme) {
        this.colorTheme = colorTheme
    }
}
