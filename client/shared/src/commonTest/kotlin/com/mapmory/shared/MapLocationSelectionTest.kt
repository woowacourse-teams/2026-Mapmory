package com.mapmory.shared

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapLocationSelectionTest {
    @Test
    fun `기존_코드가_충돌하면_지역_코드보다_지도_이름을_먼저_일치시킨다`() {
        val busanDistricts = listOf(
            location(code = "26110", name = "중구"),
            location(code = "26410", name = "금정구"),
        )

        assertEquals(
            "26410",
            findMapDistrictLocation("금정구", busanDistricts)?.regionCode,
        )
        assertEquals(
            "26110",
            findMapDistrictLocation("중구", busanDistricts)?.regionCode,
        )
    }

    @Test
    fun `지역을_일치시키기_전에_시도_이름을_제거한다`() {
        val district = location(code = "26410", name = "금정구")

        assertEquals(
            district,
            findMapDistrictLocation("부산광역시 금정구", listOf(district)),
        )
    }

    @Test
    fun `지도_이름이_어떤_지역과도_일치하지_않으면_추측하지_않는다`() {
        val district = location(code = "26110", name = "중구")

        assertNull(findMapDistrictLocation("금정구", listOf(district)))
    }

    @Test
    fun `정규_코드와_시도로_지역을_조회한다`() {
        val province = Location(
            id = 4L,
            countryId = 1L,
            parentId = null,
            regionCode = "KR-26",
            name = "부산광역시",
            type = LocationType.PROVINCE,
        )
        val district = location(code = "26410", name = "금정구")

        assertEquals(
            district,
            findMapDistrictLocation("26410", "KR-26", listOf(province, district)),
        )
        assertNull(findMapDistrictLocation("26410", "KR-27", listOf(province, district)))
    }

    private fun location(code: String, name: String): Location = Location(
        id = code.toLong(),
        countryId = 1L,
        parentId = 4L,
        regionCode = code,
        name = name,
        type = LocationType.DISTRICT,
    )
}
