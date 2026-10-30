package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.presentation.map.domain.ProvincePolygon

/** Generated from a development-time GeoJSON source. */
internal object GeneratedKoreaMapData {
    private val fragments: List<ProvincePolygon> = listOf(
        GeneratedKoreaMapDataPart00.provinces,
        GeneratedKoreaMapDataPart01.provinces,
        GeneratedKoreaMapDataPart02.provinces,
        GeneratedKoreaMapDataPart03.provinces,
        GeneratedKoreaMapDataPart04.provinces,
    ).flatten()

    val provinces: List<ProvincePolygon> = fragments
        .groupBy(ProvincePolygon::code)
        .values
        .map { provinceFragments ->
            val first = provinceFragments.first()
            first.copy(rings = provinceFragments.flatMap { it.rings })
        }
}
