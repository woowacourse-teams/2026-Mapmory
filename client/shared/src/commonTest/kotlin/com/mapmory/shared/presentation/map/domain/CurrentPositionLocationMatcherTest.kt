package com.mapmory.shared.presentation.map.domain

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentPositionLocationMatcherTest {
    @Test
    fun `현재 좌표를 경계 안의 백엔드 시군구 항목으로 매칭한다`() {
        val expected = location("11110")

        val result = findCurrentTripRecordLocation(
            point = GeoPoint(longitude = 5f, latitude = 5f),
            districtBoundaries = listOf(
                ProvincePolygon(
                    code = expected.regionCode,
                    name = expected.name,
                    rings = listOf(square(0f, 0f, 10f, 10f)),
                ),
            ),
            countryBoundaries = emptyList(),
            locations = listOf(expected),
        )

        assertEquals(expected, result)
    }

    @Test
    fun `외국 좌표는 백엔드 국가 항목으로 매칭한다`() {
        val expected = Location(
            id = 392L,
            countryId = 2L,
            parentId = null,
            regionCode = "JP",
            name = "일본",
            type = LocationType.PROVINCE,
        )

        val result = findCurrentTripRecordLocation(
            point = GeoPoint(longitude = 5f, latitude = 5f),
            districtBoundaries = emptyList(),
            countryBoundaries = listOf(
                CountryPolygon("JP", "Japan", listOf(square(0f, 0f, 10f, 10f))),
            ),
            locations = listOf(expected),
        )

        assertEquals(expected, result)
    }

    @Test
    fun `백엔드에 없는 지역은 매칭하지 않는다`() {
        val result = findCurrentTripRecordLocation(
            point = GeoPoint(longitude = 15f, latitude = 5f),
            districtBoundaries = listOf(
                ProvincePolygon("11110", "종로구", listOf(square(0f, 0f, 10f, 10f))),
                ProvincePolygon("99999", "미등록구", listOf(square(10f, 0f, 20f, 10f))),
            ),
            countryBoundaries = emptyList(),
            locations = listOf(location("11110")),
        )

        assertNull(result)
    }

    private fun location(code: String) = Location(
        id = code.toLong(),
        countryId = 1L,
        parentId = null,
        regionCode = code,
        name = "종로구",
        type = LocationType.DISTRICT,
    )

    private fun square(left: Float, bottom: Float, right: Float, top: Float) = listOf(
        GeoPoint(left, bottom),
        GeoPoint(right, bottom),
        GeoPoint(right, top),
        GeoPoint(left, top),
        GeoPoint(left, bottom),
    )
}
