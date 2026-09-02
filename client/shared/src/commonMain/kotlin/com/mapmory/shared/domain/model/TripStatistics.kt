package com.mapmory.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TripStatistics(
    val recordCount: Long,
    val mediaCount: Long,
    val visitedCountryCount: Long,
    val visitedKoreaDistrictCount: Long,
    val visitedCountryCodes: List<String>,
    val topRegions: List<TopRegionStatistics>,
)

@Serializable
data class TopRegionStatistics(
    val regionId: Long,
    val code: String,
    val type: MapRegionType,
    val name: String,
    val recordCount: Long,
)
