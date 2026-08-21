package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.domain.model.KoreanSelectableDistrictCodes
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.ProvincePolygon
import com.mapmory.shared.presentation.map.ui.regionAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KoreaMapStaticDataTest {
    @Test
    fun everyMapSelectableDistrictUsesAKnownProvinceCode() {
        val provinceCodes = GeneratedKoreaMapData.provinces.map { it.code }
        val mapSelectableDistricts = KoreanSelectableDistrictCodes.filter { it.provinceCode != null }

        assertEquals(17, provinceCodes.size)
        assertTrue(mapSelectableDistricts.isNotEmpty())
        assertTrue(mapSelectableDistricts.all { it.provinceCode in provinceCodes })
    }

    @Test
    fun knownDistrictSelectionsUseCanonicalCodes() {
        assertEquals("51760", KoreanSelectableDistrictCodes.single { it.code == "51760" }.code)
        assertEquals("11680", KoreanSelectableDistrictCodes.single { it.code == "11680" }.code)
    }

    @Test
    fun provinceSelectionCanResolveOnlyItsCanonicalDistricts() {
        val gangwon = KoreanSelectableDistrictCodes.filter { it.provinceCode == "KR-42" }

        assertTrue(gangwon.isNotEmpty())
        assertTrue(gangwon.all { it.provinceCode == "KR-42" })
        assertTrue(gangwon.none { it.provinceCode != "KR-42" })
    }

    @Test
    fun generatedProvinceAndWorldDataSupportMapScopeSwitch() {
        assertEquals(17, GeneratedKoreaMapData.provinces.size)
        assertTrue(GeneratedWorldMapData.countries.isNotEmpty())
    }

    @Test
    fun selectsTheSmallestExactBoundaryWhenRegionsOverlap() {
        val province = square("KR-41", "경기도", 0f, 0f, 10f, 10f)
        val city = square("KR-11", "서울특별시", 2f, 2f, 4f, 4f)

        assertEquals("KR-11", listOf(province, city).regionAt(GeoPoint(3f, 3f))?.code)
        assertEquals("KR-41", listOf(province, city).regionAt(GeoPoint(1f, 1f))?.code)
        assertEquals(null, listOf(province, city).regionAt(GeoPoint(11f, 11f)))
    }

    @Test
    fun generatedMapKeepsSeoulAndGyeonggiBoundariesDistinct() {
        assertEquals(
            "KR-11",
            GeneratedKoreaMapData.provinces.regionAt(GeoPoint(126.98f, 37.56f))?.code,
        )
        assertEquals(
            "KR-41",
            GeneratedKoreaMapData.provinces.regionAt(GeoPoint(127.20f, 37.40f))?.code,
        )
    }

    @Test
    fun adjacentProvinceBoundariesShareCoordinatesWithoutVisibleGaps() {
        assertTrue(sharedBoundaryEdgeCount("KR-31", "KR-48") > 0, "울산과 경남 경계가 분리되어 있습니다")
        assertTrue(sharedBoundaryEdgeCount("KR-50", "KR-44") > 0, "세종과 충남 경계가 분리되어 있습니다")
    }

    private fun sharedBoundaryEdgeCount(firstCode: String, secondCode: String): Int {
        val regions = GeneratedKoreaMapData.provinces.associateBy { it.code }
        fun edges(code: String): Set<Set<GeoPoint>> = regions.getValue(code).rings
            .flatMap { ring -> ring.zipWithNext { start, end -> setOf(start, end) } }
            .toSet()
        return edges(firstCode).intersect(edges(secondCode)).size
    }

    private fun square(
        code: String,
        name: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): ProvincePolygon = ProvincePolygon(
        code = code,
        name = name,
        rings = listOf(
            listOf(
                GeoPoint(left, top),
                GeoPoint(right, top),
                GeoPoint(right, bottom),
                GeoPoint(left, bottom),
            ),
        ),
    )
}
