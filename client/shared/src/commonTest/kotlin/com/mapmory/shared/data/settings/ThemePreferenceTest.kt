package com.mapmory.shared.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemePreferenceTest {
    @Test
    fun `기존_색상_테마_저장값을_새_팔레트로_변환한다`() {
        assertEquals(MapmoryThemePalette.OCEAN_BLUE, legacyThemePalette("BLUE"))
        assertEquals(MapmoryThemePalette.MAPMORY_GREEN, legacyThemePalette("GREEN"))
        assertNull(legacyThemePalette(null))
        assertNull(legacyThemePalette("unknown"))
    }

    @Test
    fun `색상_테마와_다크_모드는_독립적으로_저장한다`() {
        val preference = MemoryThemePreference()
        assertEquals(MapmoryThemePalette.MAPMORY_GREEN, preference.loadThemePalette())
        preference.saveThemePalette(MapmoryThemePalette.OCEAN_BLUE)
        preference.saveIsDarkTheme(true)
        assertEquals(MapmoryThemePalette.OCEAN_BLUE, preference.loadThemePalette())
        preference.saveThemePalette(MapmoryThemePalette.MAPMORY_GREEN)
        assertTrue(preference.loadIsDarkTheme())
    }

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
