package com.mapmory.shared.presentation.map.domain

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.presentation.photo.PhotoRecommendationRegion

internal fun findCurrentTripRecordLocation(
    point: GeoPoint,
    districtBoundaries: List<ProvincePolygon>,
    countryBoundaries: List<CountryPolygon>,
    locations: List<Location>,
): Location? {
    val districtsByCode = locations.asSequence()
        .filter { it.countryId == KoreaCountryId && it.type == LocationType.DISTRICT }
        .associateBy(Location::regionCode)

    val koreanDistrict = districtBoundaries.firstNotNullOfOrNull { boundary ->
        val location = districtsByCode[boundary.code] ?: return@firstNotNullOfOrNull null
        location.takeIf {
            PhotoRecommendationRegion(boundary.code, boundary.rings)
                .contains(point.latitude.toDouble(), point.longitude.toDouble())
        }
    }
    if (koreanDistrict != null) return koreanDistrict

    val countriesByCode = locations.asSequence()
        .filter { it.countryId != KoreaCountryId && it.type == LocationType.PROVINCE }
        .associateBy(Location::regionCode)

    return countryBoundaries.firstNotNullOfOrNull { boundary ->
        val location = countriesByCode[boundary.code] ?: return@firstNotNullOfOrNull null
        location.takeIf {
            PhotoRecommendationRegion(boundary.code, boundary.rings)
                .contains(point.latitude.toDouble(), point.longitude.toDouble())
        }
    }
}

private const val KoreaCountryId = 1L
