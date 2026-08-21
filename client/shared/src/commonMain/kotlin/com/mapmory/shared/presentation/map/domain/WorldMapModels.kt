package com.mapmory.shared.presentation.map.domain

data class GeoPoint(
    val longitude: Float,
    val latitude: Float,
)

data class CountryPolygon(
    val code: String,
    val name: String,
    /** Outer rings of the country. A country may contain multiple islands. */
    val rings: List<List<GeoPoint>>,
)


data class ProvincePolygon(
    val code: String,
    val name: String,
    /** Exterior rings only. Interior GeoJSON rings (holes) are discarded at generation time. */
    val rings: List<List<GeoPoint>>,
)
