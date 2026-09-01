package com.mapmory.shared

import com.mapmory.shared.data.local.normalizeRegionName
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType

internal fun findMapDistrictLocation(
    regionCode: String,
    provinceCode: String,
    locations: List<Location>,
): Location? {
    val locationsById = locations.associateBy(Location::id)
    return locations.firstOrNull { location ->
        location.type == LocationType.DISTRICT &&
            location.regionCode == regionCode &&
            locationsById[location.parentId]?.regionCode == provinceCode
    }
}

internal fun findMapDistrictLocation(
    mapRegionName: String,
    districtLocations: List<Location>,
): Location? {
    val normalizedMapRegionName = normalizeRegionName(mapRegionName)
    return districtLocations.singleOrNull { location ->
        normalizeRegionName(location.name) == normalizedMapRegionName
    }
}
