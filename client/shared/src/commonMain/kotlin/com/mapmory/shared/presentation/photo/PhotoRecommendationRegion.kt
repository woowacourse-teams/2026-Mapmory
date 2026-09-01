package com.mapmory.shared.presentation.photo

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.KoreanSelectableDistrictCodes
import com.mapmory.shared.presentation.map.data.GeneratedKoreaDistrictMapData
import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.data.GeneratedWorldMapData
import com.mapmory.shared.presentation.map.domain.GeoPoint

/**
 * Administrative boundary used by photo recommendations.
 *
 * The bounds are the high-recall pass that replaces the old generous radius around a geocoded
 * center. Point-in-polygon is the precise pass that replaces one reverse-geocoding request per
 * candidate photo.
 */
internal class PhotoRecommendationRegion(
    val code: String,
    rings: List<List<GeoPoint>>,
) {
    private val boundedRings = rings.mapNotNull { ring ->
        boundsOf(ring)?.let { bounds -> BoundedRing(ring, bounds) }
    }

    fun contains(latitude: Double, longitude: Double): Boolean {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        val point = GeoPoint(longitude.toFloat(), latitude.toFloat())
        return boundedRings.any { boundedRing ->
            boundedRing.bounds.contains(point) && pointInRing(point, boundedRing.points)
        }
    }
}

internal data class LocatedPhoto<T>(
    val value: T,
    val latitude: Double,
    val longitude: Double,
)

internal fun <T> selectPhotosInRegion(
    candidatesNewestFirst: Sequence<LocatedPhoto<T>>,
    region: PhotoRecommendationRegion,
    limit: Int? = null,
): List<T> {
    if (limit != null && limit <= 0) return emptyList()
    val matchingPhotos = candidatesNewestFirst
        .filter { candidate -> region.contains(candidate.latitude, candidate.longitude) }
        .map(LocatedPhoto<T>::value)
    return if (limit == null) matchingPhotos.toList() else matchingPhotos.take(limit).toList()
}

internal class PhotoRecommendationRegionNotFoundException(
    location: Location,
) : IllegalStateException(
    "사진 추천 지역 경계를 찾지 못했습니다: code=${location.regionCode}, type=${location.type}",
)

internal suspend fun Location.photoRecommendationRegion(): PhotoRecommendationRegion {
    val boundary = when {
        countryId != KoreaCountryId -> GeneratedWorldMapData.countries
            .firstOrNull { country -> country.code == regionCode }
            ?.let { country -> country.code to country.rings }

        type == LocationType.PROVINCE -> GeneratedKoreaMapData.provinces
            .firstOrNull { province -> province.code == regionCode }
            ?.let { province -> province.code to province.rings }

        else -> KoreanSelectableDistrictCodes
            .firstOrNull { district -> district.code == regionCode }
            ?.provinceCode
            ?.let { provinceCode -> GeneratedKoreaDistrictMapData.forProvince(provinceCode) }
            ?.firstOrNull { district -> district.code == regionCode }
            ?.let { district -> district.code to district.rings }
    } ?: throw PhotoRecommendationRegionNotFoundException(this)

    if (boundary.second.none { ring -> ring.size >= MinimumRingPointCount }) {
        throw PhotoRecommendationRegionNotFoundException(this)
    }

    return PhotoRecommendationRegion(boundary.first, boundary.second)
}

private data class BoundedRing(
    val points: List<GeoPoint>,
    val bounds: GeoBounds,
)

private data class GeoBounds(
    val minLongitude: Float,
    val maxLongitude: Float,
    val minLatitude: Float,
    val maxLatitude: Float,
) {
    fun contains(point: GeoPoint): Boolean =
        point.longitude in minLongitude..maxLongitude &&
            point.latitude in minLatitude..maxLatitude
}

private fun boundsOf(ring: List<GeoPoint>): GeoBounds? {
    if (ring.size < MinimumRingPointCount) return null
    return GeoBounds(
        minLongitude = ring.minOf(GeoPoint::longitude),
        maxLongitude = ring.maxOf(GeoPoint::longitude),
        minLatitude = ring.minOf(GeoPoint::latitude),
        maxLatitude = ring.maxOf(GeoPoint::latitude),
    )
}

private fun pointInRing(point: GeoPoint, ring: List<GeoPoint>): Boolean {
    if (ring.size < MinimumRingPointCount) return false

    var inside = false
    var previous = ring.last()
    ring.forEach { current ->
        if (pointOnSegment(point, previous, current)) return true
        val crossesLatitude =
            (current.latitude > point.latitude) != (previous.latitude > point.latitude)
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

private fun pointOnSegment(point: GeoPoint, start: GeoPoint, end: GeoPoint): Boolean {
    val cross = (point.latitude - start.latitude) * (end.longitude - start.longitude) -
        (point.longitude - start.longitude) * (end.latitude - start.latitude)
    if (kotlin.math.abs(cross) > BoundaryEpsilon) return false
    return point.longitude in minOf(start.longitude, end.longitude)..maxOf(start.longitude, end.longitude) &&
        point.latitude in minOf(start.latitude, end.latitude)..maxOf(start.latitude, end.latitude)
}

internal const val MaxRecommendedPhotos = 12
internal const val PhotoRecommendationRegionNotFoundMessage = "선택한 장소의 경계를 확인하지 못했어요."
private const val KoreaCountryId = 1L
private const val MinimumRingPointCount = 3
private const val BoundaryEpsilon = 0.000001f
