package com.mapmory.shared.presentation.map.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KoreaMapLabelZoomTest {
    @Test
    fun labelSizeGrowsGraduallyAsTheMapZooms() {
        val initial = koreaMapLabelZoomScale(1f)
        val medium = koreaMapLabelZoomScale(8f)
        val close = koreaMapLabelZoomScale(15f)

        assertEquals(1f, initial)
        assertTrue(medium > initial)
        assertTrue(close > medium)
    }

    @Test
    fun labelSizeStopsGrowingAfterTheMaximumMapZoom() {
        assertEquals(koreaMapLabelZoomScale(18f), koreaMapLabelZoomScale(24f))
    }
}
