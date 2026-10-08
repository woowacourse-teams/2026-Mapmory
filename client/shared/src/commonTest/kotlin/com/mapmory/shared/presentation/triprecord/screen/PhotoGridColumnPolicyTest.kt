package com.mapmory.shared.presentation.triprecord.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoGridColumnPolicyTest {
    @Test
    fun `핀치로_축소하면_열_수가_늘고_확대하면_줄어든다`() {
        assertEquals(
            PhotoGridPinchResult(columnCount = 3, accumulatedZoom = 1f),
            photoGridPinchResultAfterZoom(current = 2, accumulatedZoom = 0.8f),
        )
        assertEquals(
            PhotoGridPinchResult(columnCount = 1, accumulatedZoom = 1f),
            photoGridPinchResultAfterZoom(current = 2, accumulatedZoom = 1.25f),
        )
    }

    @Test
    fun `변경_임계값에_도달하지_않으면_열_수를_유지한다`() {
        assertNull(photoGridPinchResultAfterZoom(current = 2, accumulatedZoom = 1f))
    }

    @Test
    fun `핀치 임계값을_초과한_배율을_다음_열_변경에_이어_사용한다`() {
        assertEquals(
            PhotoGridPinchResult(columnCount = 1, accumulatedZoom = 1.2f),
            photoGridPinchResultAfterZoom(current = 2, accumulatedZoom = 1.5f),
        )
        assertEquals(
            PhotoGridPinchResult(columnCount = 3, accumulatedZoom = 0.875f),
            photoGridPinchResultAfterZoom(current = 2, accumulatedZoom = 0.7f),
        )
    }

    @Test
    fun `열_수는_한_열부터_네_열_사이로_제한한다`() {
        assertEquals(
            PhotoGridPinchResult(columnCount = 1, accumulatedZoom = 1f),
            photoGridPinchResultAfterZoom(current = 1, accumulatedZoom = 1.5f),
        )
        assertEquals(
            PhotoGridPinchResult(columnCount = 4, accumulatedZoom = 1f),
            photoGridPinchResultAfterZoom(current = 4, accumulatedZoom = 0.7f),
        )
    }
}
