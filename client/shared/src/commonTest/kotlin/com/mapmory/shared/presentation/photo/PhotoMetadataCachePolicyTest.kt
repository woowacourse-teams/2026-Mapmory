package com.mapmory.shared.presentation.photo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoMetadataCachePolicyTest {
    @Test
    fun reusesCoordinatesOnlyWhenMediaStoreTimestampAndCoordinatesArePresent() {
        assertTrue(shouldReuseCoordinates(10L, 37.5, 127.0, 10L))
        assertFalse(shouldReuseCoordinates(11L, 37.5, 127.0, 10L))
        assertFalse(shouldReuseCoordinates(10L, null, 127.0, 10L))
        assertFalse(shouldReuseCoordinates(10L, 37.5, null, 10L))
    }
}
