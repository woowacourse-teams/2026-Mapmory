package com.mapmory.shared.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemePreferenceTest {
    @Test
    fun `저장한_테마를_다시_읽는다`() {
        val preference = MemoryThemePreference()

        assertFalse(preference.loadIsDarkTheme())

        preference.saveIsDarkTheme(true)

        assertTrue(preference.loadIsDarkTheme())
    }

    @Test
    fun `색상_테마를_저장하고_다시_읽는다`() {
        val preference = MemoryThemePreference()

        assertEquals(MapmoryThemePalette.MAPMORY_GREEN, preference.loadThemePalette())

        preference.saveThemePalette(MapmoryThemePalette.OCEAN_BLUE)

        assertEquals(MapmoryThemePalette.OCEAN_BLUE, preference.loadThemePalette())
    }

    @Test
    fun `알_수_없는_색상_테마는_기본_그린으로_돌아간다`() {
        assertEquals(MapmoryThemePalette.MAPMORY_GREEN, MapmoryThemePalette.fromStorageValue(null))
        assertEquals(MapmoryThemePalette.MAPMORY_GREEN, MapmoryThemePalette.fromStorageValue("unknown"))
    }
}
