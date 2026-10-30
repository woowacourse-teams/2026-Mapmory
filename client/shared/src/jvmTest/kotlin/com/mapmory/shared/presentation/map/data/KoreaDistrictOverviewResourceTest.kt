package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.domain.model.KoreanSelectableDistrictCodes
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.ProvincePolygon
import com.mapmory.shared.presentation.map.ui.regionAt
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KoreaDistrictOverviewResourceTest {
    @Test
    fun `전국_채움은_정규_시군구_코드와_성남시_접합부를_유지한다`() = runSuspend {
        val districts = GeneratedKoreaDistrictMapData.overviewDistricts()

        assertEquals(230, districts.size)
        assertEquals(KoreanSelectableDistrictCodes.map { it.code }.toSet(), districts.map { it.code }.toSet())
        assertTrue(districts.all { district -> district.rings.isNotEmpty() })
        assertEquals("41130", districts.regionAt(GeoPoint(127.121f, 37.413f))?.code)
    }

    @Test
    fun `모든_시도_외곽은_같은_시군구_채움의_바깥_변과_정확히_일치한다`() = runSuspend {
        val districts = GeneratedKoreaDistrictMapData.overviewDistricts()
        val provinceCodes = GeneratedKoreaDistrictMapData.overviewData().labels.associate { it.code to it.provinceCode }
        val provinces = GeneratedKoreaDistrictMapData.overviewProvinces()

        assertEquals(GeneratedKoreaMapData.provinces.map { it.code }.toSet(), provinces.map { it.code }.toSet())
        provinces.forEach { province ->
            val edgeCounts = districts.filter { provinceCodes.getValue(it.code) == province.code }
                .flatMap { it.edges() }.groupingBy { it }.eachCount()
            val exterior = edgeCounts.filterValues { it == 1 }.keys
            assertEquals(exterior, province.edges().toSet(), province.code)
            assertEquals(exterior.size, province.edges().size, "duplicate province edge: ${province.code}")
            assertTrue(province.rings.all { it.size >= 4 && it.first() == it.last() }, province.code)
        }
    }

    @Test
    fun `모든_옅은_경계선도_채움과_동일한_변을_사용한다`() = runSuspend {
        val fillEdges = GeneratedKoreaDistrictMapData.overviewDistricts().flatMap { it.edges() }.toSet()
        val lineEdges = GeneratedKoreaDistrictMapData.overviewData().lines.flatMap { line ->
            line.zipWithNext { start, end -> setOf(start, end) }
        }.toSet()
        assertTrue(lineEdges.isNotEmpty())
        assertEquals(emptySet(), lineEdges - fillEdges)
    }

    private fun ProvincePolygon.edges(): List<Set<GeoPoint>> = rings.flatMap { ring ->
        ring.zipWithNext { start, end -> setOf(start, end) }.filter { it.size == 2 }
    }
}
