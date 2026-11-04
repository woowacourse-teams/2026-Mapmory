package com.mapmory.shared.data.settings

import com.mapmory.shared.MapmoryColorTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemePreferenceTest {
    @Test
    fun `색상_테마와_다크_모드는_독립적으로_저장한다`() {
        val preference = MemoryThemePreference()
        assertEquals(MapmoryColorTheme.GREEN, preference.loadColorTheme())
        preference.saveColorTheme(MapmoryColorTheme.BLUE)
        preference.saveIsDarkTheme(true)
        assertEquals(MapmoryColorTheme.BLUE, preference.loadColorTheme())
        preference.saveColorTheme(MapmoryColorTheme.GREEN)
        assertTrue(preference.loadIsDarkTheme())
    }

    @Test
    fun `저장한_테마를_다시_읽는다`() {
        val preference = MemoryThemePreference()

        assertFalse(preference.loadIsDarkTheme())

        preference.saveIsDarkTheme(true)

        assertTrue(preference.loadIsDarkTheme())
    }
}
