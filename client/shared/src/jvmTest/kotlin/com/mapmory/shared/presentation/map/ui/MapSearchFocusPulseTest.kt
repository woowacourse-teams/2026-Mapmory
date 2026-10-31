package com.mapmory.shared.presentation.map.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class MapSearchFocusPulseTest {
    @Test
    fun `호흡_강조선은_항상_보이고_최대_밝기가_제한된다`() {
        assertEquals(0.60f, mapSearchFocusOutlineAlpha(-1f), 0.001f)
        assertEquals(0.60f, mapSearchFocusOutlineAlpha(0f), 0.001f)
        assertEquals(0.92f, mapSearchFocusOutlineAlpha(1f), 0.001f)
        assertEquals(0.92f, mapSearchFocusOutlineAlpha(2f), 0.001f)
        assertEquals(0.04f, mapSearchFocusGlowAlpha(0f), 0.001f)
        assertEquals(0.12f, mapSearchFocusGlowAlpha(1f), 0.001f)
        assertEquals(0f, mapSearchFocusWhiteHighlightAlpha(-1f), 0.001f)
        assertEquals(0f, mapSearchFocusWhiteHighlightAlpha(0f), 0.001f)
        assertEquals(0.78f, mapSearchFocusWhiteHighlightAlpha(1f), 0.001f)
        assertEquals(0.78f, mapSearchFocusWhiteHighlightAlpha(2f), 0.001f)
    }
}
