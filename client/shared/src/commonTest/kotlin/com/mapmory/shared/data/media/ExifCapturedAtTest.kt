package com.mapmory.shared.data.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExifCapturedAtTest {
    @Test
    fun `정상_EXIF와_윤년_날짜를_정규화한다`() {
        assertEquals("2026-09-11", normalizeExifCapturedAt("2026:09:11 12:34:56"))
        assertEquals("2024-02-29", normalizeExifCapturedAt("2024:02:29 00:00:00"))
        assertEquals("2026-12-31", normalizeExifCapturedAt("2026:12:31 23:59:59"))
    }

    @Test
    fun `형식이_다르거나_잘렸거나_유효하지_않은_날짜와_시간은_저장하지_않는다`() {
        listOf(
            null, "", "2026", "2026:09:11", "2026:9:11 12:34:56",
            "2026-09-11 12:34:56", "2026:09:11T12:34:56",
            "2026:09:11 12:34:56 extra", " 2026:09:11 12:34:56",
            "2026:09:11 12:34:56 ", "0000:01:01 00:00:00",
            "2026:00:11 12:34:56", "2026:13:11 12:34:56",
            "2026:02:29 12:34:56", "2026:04:31 12:34:56",
            "2026:09:11 24:00:00", "2026:09:11 12:60:00", "2026:09:11 12:00:60",
        ).forEach { value -> assertNull(normalizeExifCapturedAt(value), value) }
    }
}
