package com.mapmory.shared

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapLocationSelectionTest {
    @Test
    fun matchesMapNameBeforeRegionCodeWhenLegacyCodesCollide() {
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
    fun removesProvinceNameBeforeMatchingDistrict() {
        val district = location(code = "26410", name = "금정구")

        assertEquals(
            district,
            findMapDistrictLocation("부산광역시 금정구", listOf(district)),
        )
    }

    @Test
    fun doesNotGuessWhenMapNameDoesNotMatchAnyDistrict() {
        val district = location(code = "26110", name = "중구")

        assertNull(findMapDistrictLocation("금정구", listOf(district)))
    }

    @Test
    fun resolvesDistrictByCanonicalCodeAndProvince() {
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
