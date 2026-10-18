package com.mapmory.shared.presentation.map.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class KoreaMapPinchTest {
    private val viewport = IntSize(1000, 800)
    private val center = Offset(500f, 400f)

    @Test
    fun zoomKeepsTheMapPointUnderTheFingersEvenWhenPanned() {
        val current = MapTransform(2f, Offset(120f, -70f))
        val fingers = Offset(800f, 650f)
        val basePoint = center + (fingers - center - current.pan) / current.zoom
        listOf(0.6f, 1.5f).forEach { scale ->
            val movement = Offset(20f, -15f)
            val next = transformKoreaMapGesture(current, fingers, movement, scale, viewport)
            val screenPoint = center + (basePoint - center) * next.zoom + next.pan
            assertEquals(fingers.x + movement.x, screenPoint.x, 0.001f)
            assertEquals(fingers.y + movement.y, screenPoint.y, 0.001f)
        }
    }

    @Test
    fun zoomLimitsUseEffectiveScaleAndDoNotMoveMapWhenAlreadyAtLimit() {
        listOf(MapTransform(1f, Offset(20f, 30f)) to 0.5f, MapTransform(12f, Offset(20f, 30f)) to 2f)
            .forEach { (current, scale) ->
                val next = transformKoreaMapGesture(current, Offset(800f, 600f), Offset.Zero, scale, viewport)
                assertEquals(current, next)
            }
    }

    @Test
    fun purePanMovesByFingerDistanceAndOverscrollHasResistance() {
        val current = MapTransform(2f, Offset(30f, 40f))
        assertEquals(Offset(50f, 30f), transformKoreaMapGesture(current, center, Offset(20f, -10f), 1f, viewport).pan)
        assertEquals(Offset(120f, -90f), resistKoreaMapOverscroll(Offset(200f, -130f), Offset(100f, -80f)))
    }

    @Test
    fun crossingViewportSizeDoesNotJumpForOffCenterMap() {
        fun bound(width: Float) = clampKoreaMapPan(
            Offset(500f, 0f), 1f, viewport, 100f, 100f, width, 600f,
        ).x
        assertEquals(bound(1000f), bound(1000.01f), 0.02f)
        assertEquals(bound(999.99f), bound(1000f), 0.02f)
    }
}
