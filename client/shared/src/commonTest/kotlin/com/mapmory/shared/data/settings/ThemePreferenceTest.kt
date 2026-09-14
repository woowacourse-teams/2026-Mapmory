package com.mapmory.shared.data.settings

import kotlin.test.Test
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
}
