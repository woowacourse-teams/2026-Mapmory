package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.ProvincePolygon
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mapmoryclient.shared.generated.resources.Res

/**
 * Reads bundled overview and province geometry. Boundary data is not a business-data cache and is
 * never fetched from the network at runtime.
 */
internal object GeneratedKoreaDistrictMapData {
    private val json = Json
    var cachedOverviewData: OverviewDistrictData? = null
        private set
    var cachedOverviewDistricts: List<ProvincePolygon>? = null
        private set
    private val supportedProvinceCodes = setOf(
        "11", "26", "27", "28", "29", "30", "31", "41", "42",
        "43", "44", "45", "46", "47", "48", "49", "50",
    )

    suspend fun forProvince(provinceCode: String): List<ProvincePolygon> {
        val suffix = provinceCode.removePrefix("KR-")
        require(suffix in supportedProvinceCodes) { "선택한 지역의 지도를 불러오지 못했습니다." }

        val file = Res.readBytes("files/korea-districts-$suffix.json")
            .decodeToString()
        return json.decodeFromString<DistrictResource>(file).districts.toPolygons()
    }

    suspend fun overviewData(): OverviewDistrictData {
        cachedOverviewData?.let { return it }
        val file = Res.readBytes("files/korea-district-overview.json").decodeToString()
        val resource = json.decodeFromString<OverviewBoundaryResource>(file)
        return OverviewDistrictData(
            lines = resource.lines.map { line ->
                line.map { point -> GeoPoint(point[0].toFloat(), point[1].toFloat()) }
            },
            labels = resource.labels.map { label ->
                OverviewDistrictLabel(
                    code = label.code,
                    provinceCode = label.provinceCode,
                    name = label.name,
                    point = GeoPoint(label.point[0].toFloat(), label.point[1].toFloat()),
                )
            },
        ).also { cachedOverviewData = it }
    }

    suspend fun overviewDistricts(): List<ProvincePolygon> {
        cachedOverviewDistricts?.let { return it }
        val file = Res.readBytes("files/korea-district-overview-polygons.json").decodeToString()
        return json.decodeFromString<OverviewDistrictPolygonResource>(file)
            .districts
            .toPolygons()
            .also { cachedOverviewDistricts = it }
    }
}

internal data class OverviewDistrictData(
    val lines: List<List<GeoPoint>>,
    val labels: List<OverviewDistrictLabel>,
)

internal data class OverviewDistrictLabel(
    val code: String,
    val provinceCode: String,
    val name: String,
    val point: GeoPoint,
)

private fun List<DistrictBoundary>.toPolygons(): List<ProvincePolygon> = map { district ->
    ProvincePolygon(
        code = district.code,
        name = district.name,
        rings = district.rings.map { ring ->
            ring.map { point -> GeoPoint(point[0].toFloat(), point[1].toFloat()) }
        },
    )
}

@Serializable
private data class DistrictResource(
    val provinceCode: String,
    val districts: List<DistrictBoundary>,
)

@Serializable
private data class OverviewBoundaryResource(
    val lines: List<List<List<Double>>>,
    val labels: List<OverviewDistrictLabelResource> = emptyList(),
)

@Serializable
private data class OverviewDistrictPolygonResource(
    val districts: List<DistrictBoundary>,
)

@Serializable
private data class OverviewDistrictLabelResource(
    val code: String,
    val provinceCode: String,
    val name: String,
    val point: List<Double>,
)

@Serializable
private data class DistrictBoundary(
    val code: String,
    val name: String,
    val provinceCode: String? = null,
    val rings: List<List<List<Double>>>,
)
