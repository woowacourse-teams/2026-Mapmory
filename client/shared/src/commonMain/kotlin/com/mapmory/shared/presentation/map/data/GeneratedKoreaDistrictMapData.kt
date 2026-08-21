package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.ProvincePolygon
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mapmoryclient.shared.generated.resources.Res

/**
 * Reads one generated province resource at a time. Boundary geometry is bundled with the app;
 * it is not a business-data cache and is never fetched from the network at runtime.
 */
internal object GeneratedKoreaDistrictMapData {
    private val json = Json
    private val supportedProvinceCodes = setOf(
        "11", "26", "27", "28", "29", "30", "31", "41", "42",
        "43", "44", "45", "46", "47", "48", "49", "50",
    )

    suspend fun forProvince(provinceCode: String): List<ProvincePolygon> {
        val suffix = provinceCode.removePrefix("KR-")
        require(suffix in supportedProvinceCodes) { "지원하지 않는 시·도 코드입니다: $provinceCode" }

        val file = Res.readBytes("files/korea-districts-$suffix.json")
            .decodeToString()
        return json.decodeFromString<DistrictResource>(file).districts.map { district ->
            ProvincePolygon(
                code = district.code,
                name = district.name,
                rings = district.rings.map { ring ->
                    ring.map { point -> GeoPoint(point[0].toFloat(), point[1].toFloat()) }
                },
            )
        }
    }
}

@Serializable
private data class DistrictResource(
    val provinceCode: String,
    val districts: List<DistrictBoundary>,
)

@Serializable
private data class DistrictBoundary(
    val code: String,
    val name: String,
    val rings: List<List<List<Double>>>,
)
