package com.mapmory.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MapRegionSummary(
    val regionId: Long,
    val code: String,
    val type: MapRegionType,
    val name: String,
    val count: Long,
    val level: MapRegionLevel,
)

@Serializable
enum class MapRegionType {
    COUNTRY,
    PROVINCE,
    DISTRICT,
}

@Serializable
enum class MapRegionLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}
