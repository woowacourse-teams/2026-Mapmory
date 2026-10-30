package com.mapmory.shared.presentation.photo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoMetadataCachePolicyTest {
    @Test
    fun `MediaStore_수정_시각이_같으면_GPS_유무와_관계없이_위치_조회_결과를_재사용한다`() {
        assertTrue(shouldReuseLocationMetadata(10L, 10L))
        assertFalse(shouldReuseLocationMetadata(null, 10L))
        assertFalse(shouldReuseLocationMetadata(11L, 10L))
    }
}
