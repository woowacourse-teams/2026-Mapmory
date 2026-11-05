package com.mapmory.shared.data.settings

enum class MapmoryThemePalette(val storageValue: String) {
    MAPMORY_GREEN("mapmory_green"),
    OCEAN_BLUE("ocean_blue");

    companion object {
        fun fromStorageValue(value: String?): MapmoryThemePalette =
            values().firstOrNull { it.storageValue == value } ?: MAPMORY_GREEN
    }
}

interface ThemePreference {
    fun loadIsDarkTheme(): Boolean

    fun saveIsDarkTheme(isDarkTheme: Boolean)

    fun loadThemePalette(): MapmoryThemePalette

    fun saveThemePalette(themePalette: MapmoryThemePalette)
}

class MemoryThemePreference(
    initialIsDarkTheme: Boolean = false,
    initialThemePalette: MapmoryThemePalette = MapmoryThemePalette.MAPMORY_GREEN,
) : ThemePreference {
    private var isDarkTheme = initialIsDarkTheme
    private var themePalette = initialThemePalette

    override fun loadIsDarkTheme(): Boolean = isDarkTheme

    override fun saveIsDarkTheme(isDarkTheme: Boolean) {
        this.isDarkTheme = isDarkTheme
    }

    override fun loadThemePalette(): MapmoryThemePalette = themePalette

    override fun saveThemePalette(themePalette: MapmoryThemePalette) {
        this.themePalette = themePalette
    }
}
