package com.mapmory.shared.data.local

import com.mapmory.shared.domain.model.KoreanCountryNames
import com.mapmory.shared.domain.model.KoreanSelectableDistrictCodes
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.presentation.map.data.GeneratedWorldMapData

class StaticRegionCatalog : RegionCatalog {
    override val locations: List<Location> = createLocations()
    private val locationsById = locations.associateBy(Location::id)

    override fun findDistrict(
        provinceCode: String,
        districtCode: String,
    ): Location? = locations.firstOrNull { location ->
        location.type == LocationType.DISTRICT &&
            location.regionCode == districtCode &&
            locationsById[location.parentId]?.regionCode == provinceCode
    }
}

internal fun normalizeRegionName(name: String): String {
    val compactName = name.replace(" ", "")
    return ProvincePrefixes.firstOrNull(compactName::startsWith)
        ?.let(compactName::removePrefix)
        ?: compactName
}

private fun createLocations(): List<Location> = buildList {
    ProvinceRegions.forEach { (id, region) ->
        add(
            Location(
                id = id,
                countryId = KoreaCountryId,
                parentId = null,
                regionCode = region.first,
                name = region.second,
                type = LocationType.PROVINCE,
            ),
        )
    }

    val koreaProvinceIds = associate { it.regionCode to it.id }
    KoreanSelectableDistrictCodes.forEachIndexed { index, district ->
        val id = when (district.code) {
            "11650" -> 3L
            "11680" -> 2L
            else -> 20_000L + index
        }
        add(
            Location(
                id = id,
                countryId = KoreaCountryId,
                parentId = district.provinceCode?.let(koreaProvinceIds::get),
                regionCode = district.code,
                name = normalizeRegionName(district.name),
                type = LocationType.DISTRICT,
            ),
        )
    }

    GeneratedWorldMapData.countries.forEachIndexed { index, country ->
        val id = 10_000L + index
        add(
            Location(
                id = id,
                countryId = id,
                parentId = null,
                regionCode = country.code,
                name = KoreanCountryNames.byCode[country.code] ?: country.name,
                type = LocationType.PROVINCE,
            ),
        )
    }
}

private const val KoreaCountryId = 1L

private val ProvinceRegions = listOf(
    1L to ("KR-11" to "서울특별시"),
    4L to ("KR-26" to "부산광역시"),
    5L to ("KR-27" to "대구광역시"),
    6L to ("KR-28" to "인천광역시"),
    7L to ("KR-29" to "광주광역시"),
    8L to ("KR-30" to "대전광역시"),
    9L to ("KR-31" to "울산광역시"),
    10L to ("KR-50" to "세종특별자치시"),
    11L to ("KR-41" to "경기도"),
    12L to ("KR-42" to "강원특별자치도"),
    13L to ("KR-43" to "충청북도"),
    14L to ("KR-44" to "충청남도"),
    15L to ("KR-45" to "전북특별자치도"),
    16L to ("KR-46" to "전라남도"),
    17L to ("KR-47" to "경상북도"),
    18L to ("KR-48" to "경상남도"),
    19L to ("KR-49" to "제주특별자치도"),
)

private val ProvincePrefixes = listOf(
    "서울특별시",
    "부산광역시",
    "대구광역시",
    "인천광역시",
    "광주광역시",
    "대전광역시",
    "울산광역시",
    "세종특별자치시",
    "경기도",
    "강원특별자치도",
    "강원도",
    "충청북도",
    "충청남도",
    "전북특별자치도",
    "전라북도",
    "전라남도",
    "경상북도",
    "경상남도",
    "제주특별자치도",
)
