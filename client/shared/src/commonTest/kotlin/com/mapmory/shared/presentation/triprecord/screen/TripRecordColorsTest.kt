package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.ui.graphics.Color
import com.mapmory.shared.data.settings.MapmoryThemePalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TripRecordColorsTest {
    @Test
    fun `오션블루는_라이트와_다크에서_파란_강조색을_쓰고_배경은_유지한다`() {
        val light = tripRecordColors(isDark = false, themePalette = MapmoryThemePalette.OCEAN_BLUE)
        val dark = tripRecordColors(isDark = true, themePalette = MapmoryThemePalette.OCEAN_BLUE)

        assertEquals(Color(0xFF246DBB), light.primary)
        assertEquals(Color(0xFF6EA9F3), dark.primary)
        assertEquals(Color(0xFFFFFEFF), light.pageBackground)
        assertEquals(Color(0xFF121518), dark.pageBackground)
        assertNotEquals(light.primary, tripRecordColors(false, MapmoryThemePalette.MAPMORY_GREEN).primary)
    }
}
