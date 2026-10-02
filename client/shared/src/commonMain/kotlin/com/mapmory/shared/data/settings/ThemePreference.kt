package com.mapmory.shared.data.settings

interface ThemePreference {
    fun loadIsDarkTheme(): Boolean

    fun saveIsDarkTheme(isDarkTheme: Boolean)
}

class MemoryThemePreference(
    initialIsDarkTheme: Boolean = false,
) : ThemePreference {
    private var isDarkTheme = initialIsDarkTheme

    override fun loadIsDarkTheme(): Boolean = isDarkTheme

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        this.isDarkTheme = isDarkTheme
    }
}
