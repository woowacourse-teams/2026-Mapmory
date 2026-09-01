package com.mapmory.shared.presentation.photo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoMetadataCachePolicyTest {
    @Test
    fun `MediaStore_수정_시각과_좌표가_모두_있을_때만_캐시_좌표를_재사용한다`() {
        assertTrue(shouldReuseCoordinates(10L, 37.5, 127.0, 10L))
        assertFalse(shouldReuseCoordinates(null, 37.5, 127.0, 10L))
        assertFalse(shouldReuseCoordinates(11L, 37.5, 127.0, 10L))
        assertFalse(shouldReuseCoordinates(10L, null, 127.0, 10L))
        assertFalse(shouldReuseCoordinates(10L, 37.5, null, 10L))
    }
}
