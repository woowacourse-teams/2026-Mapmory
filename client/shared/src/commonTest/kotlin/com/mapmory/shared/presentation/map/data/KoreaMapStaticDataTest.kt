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
    fun `모든_지도_선택_지역은_알려진_시도_코드를_사용한다`() {
        val provinceCodes = GeneratedKoreaMapData.provinces.map { it.code }
        val mapSelectableDistricts = KoreanSelectableDistrictCodes.filter { it.provinceCode != null }

        assertEquals(17, provinceCodes.size)
        assertTrue(mapSelectableDistricts.isNotEmpty())
        assertTrue(mapSelectableDistricts.all { it.provinceCode in provinceCodes })
    }

    @Test
    fun `알려진_지역_선택은_정규_코드를_사용한다`() {
        assertEquals("51760", KoreanSelectableDistrictCodes.single { it.code == "51760" }.code)
        assertEquals("11680", KoreanSelectableDistrictCodes.single { it.code == "11680" }.code)
    }

    @Test
    fun `시도_선택은_해당_시도의_정규_지역만_조회한다`() {
        val gangwon = KoreanSelectableDistrictCodes.filter { it.provinceCode == "KR-42" }

        assertTrue(gangwon.isNotEmpty())
        assertTrue(gangwon.all { it.provinceCode == "KR-42" })
        assertTrue(gangwon.none { it.provinceCode != "KR-42" })
    }

    @Test
    fun `생성된_시도와_세계_데이터는_지도_범위_전환을_지원한다`() {
        assertEquals(17, GeneratedKoreaMapData.provinces.size)
        assertTrue(GeneratedWorldMapData.countries.isNotEmpty())
    }

    @Test
    fun `지역이_겹치면_가장_작은_정확한_경계를_선택한다`() {
        val province = square("KR-41", "경기도", 0f, 0f, 10f, 10f)
        val city = square("KR-11", "서울특별시", 2f, 2f, 4f, 4f)

        assertEquals("KR-11", listOf(province, city).regionAt(GeoPoint(3f, 3f))?.code)
        assertEquals("KR-41", listOf(province, city).regionAt(GeoPoint(1f, 1f))?.code)
        assertEquals(null, listOf(province, city).regionAt(GeoPoint(11f, 11f)))
    }

    @Test
    fun `오른쪽이나_위쪽_경계선을_눌러도_지역을_선택한다`() {
        val region = square("KR-11", "서울특별시", 0f, 0f, 10f, 10f)

        assertEquals("KR-11", listOf(region).regionAt(GeoPoint(10f, 5f))?.code)
        assertEquals("KR-11", listOf(region).regionAt(GeoPoint(5f, 10f))?.code)
    }

    @Test
    fun `생성된_지도는_서울과_경기도_경계를_구분한다`() {
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
    fun `인접한_시도_경계는_좌표를_공유하고_빈틈이_없다`() {
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
