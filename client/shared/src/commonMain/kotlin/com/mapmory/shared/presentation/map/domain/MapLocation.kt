package com.mapmory.shared.presentation.map.domain

internal fun isWithinRegions(
    point: GeoPoint,
    regions: List<ProvincePolygon>,
): Boolean = regions.any { region ->
    region.rings.any { ring -> ring.containsPoint(point) }
}

private fun List<GeoPoint>.containsPoint(point: GeoPoint): Boolean {
    if (size < 3) return false

    var inside = false
    var previous = last()
    forEach { current ->
        val crossesLatitude = (current.latitude > point.latitude) != (previous.latitude > point.latitude)
        if (crossesLatitude) {
            val intersectionLongitude =
                (previous.longitude - current.longitude) *
                    (point.latitude - current.latitude) /
                    (previous.latitude - current.latitude) + current.longitude
            if (point.longitude < intersectionLongitude) inside = !inside
        }
        previous = current
    }
    return inside
}
