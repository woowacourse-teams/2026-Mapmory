package com.mapmory.shared.domain.model

data class MapRegionSummary(
    val regionId: Long,
    val code: String,
    val type: MapRegionType,
    val name: String,
    val count: Long,
    val level: MapRegionLevel,
)

enum class MapRegionType {
    COUNTRY,
    PROVINCE,
    DISTRICT,
}

enum class MapRegionLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}
