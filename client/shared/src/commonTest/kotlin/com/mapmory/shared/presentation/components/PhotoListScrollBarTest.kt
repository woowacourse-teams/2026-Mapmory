package com.mapmory.shared.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoListScrollBarTest {
    @Test
    fun grabbingThumbDoesNotJumpAndDraggingMapsFullThumbTravel() {
        assertEquals(0.5f, photoScrollFraction(480f, 30f, 1_000f, 100f))
        assertEquals(1f, photoScrollFraction(930f, 30f, 1_000f, 100f))
        assertEquals(0f, photoScrollFraction(30f, 30f, 1_000f, 100f))
    }

    @Test
    fun draggingOutsideTrackClampsToEndsAndShortTrackCannotScroll() {
        assertEquals(0f, photoScrollFraction(-100f, 30f, 1_000f, 100f))
        assertEquals(1f, photoScrollFraction(1_500f, 30f, 1_000f, 100f))
        assertEquals(0f, photoScrollFraction(20f, 10f, 40f, 40f))
    }

    @Test
    fun targetIncludesBothPaddingsAndVaryingHeaderAndPhotoRowHeights() {
        val sizes = listOf(100, 200, 300)
        assertEquals(PhotoScrollTarget(0, 0), photoScrollTarget(0f, 200, sizes, 10, 20, 30))
        assertEquals(PhotoScrollTarget(1, 125), photoScrollTarget(0.5f, 200, sizes, 10, 20, 30))
        assertEquals(PhotoScrollTarget(2, 150), photoScrollTarget(1f, 200, sizes, 10, 20, 30))
        assertNull(photoScrollTarget(1f, 200, emptyList(), 0, 0, 0))
    }
}
