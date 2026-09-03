package com.mapmory.shared.presentation.photo

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.domain.model.KoreanSelectableDistrictCodes
import com.mapmory.shared.presentation.map.data.GeneratedKoreaDistrictMapData
import kotlin.test.assertEquals

internal suspend fun assertAllSelectableKoreanCityBoundaries() {
    val missingBoundaries = mutableListOf<String>()

    KoreanSelectableDistrictCodes
        .filter { district -> district.provinceCode != null }
        .groupBy { district -> requireNotNull(district.provinceCode) }
        .forEach { (provinceCode, districts) ->
            val boundaries = GeneratedKoreaDistrictMapData
                .forProvince(provinceCode)
                .associateBy { boundary -> boundary.code }

            districts.forEach { district ->
                val boundary = boundaries[district.code]
                if (boundary == null || boundary.rings.none { ring -> ring.size >= 3 }) {
                    missingBoundaries += "${district.code}(${district.name})@$provinceCode"
                }
            }
        }

    assertEquals(
        emptyList(),
        missingBoundaries,
    )
}

internal suspend fun assertJejuAndSejongBoundaries() {
    val catalog = StaticRegionCatalog()

    listOf("50110", "50130", "36110").forEach { regionCode ->
        val location = catalog.locations.single { location -> location.regionCode == regionCode }

        assertEquals(regionCode, location.photoRecommendationRegion().code)
    }
}
