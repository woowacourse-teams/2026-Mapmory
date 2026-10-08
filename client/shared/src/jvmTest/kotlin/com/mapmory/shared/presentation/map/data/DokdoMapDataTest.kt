package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.domain.model.KoreanSelectableDistrictCodes
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.ui.sortedForMapRendering
import com.mapmory.shared.presentation.map.ui.regionAt
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DokdoMapDataTest {
    @Test
    fun `광주는_전라남도보다_위에_그려져_경계가_가려지지_않는다`() {
        val drawOrder = GeneratedKoreaMapData.provinces.sortedForMapRendering().map { it.code }

        assertTrue(drawOrder.indexOf("KR-29") > drawOrder.indexOf("KR-46"))
    }

    @Test
    fun `독도_표식은_대한민국_지도에_표시되고_울릉군으로_선택된다`() = runBlocking {
        val dokdo = GeoPoint(131.86941f, 37.24006f)
        val province = GeneratedKoreaMapData.provinces.regionAt(dokdo)
        val districts = GeneratedKoreaDistrictMapData.forProvince("KR-47")
        val district = districts.regionAt(dokdo)
        val location = StaticRegionCatalog().findByCode("47940")

        assertEquals("KR-47", province?.code)
        val provinceDokdoRings = province?.rings?.filter { it.isDokdoIslandRing() }.orEmpty()
        assertTrue(provinceDokdoRings.size >= 2)
        assertTrue(provinceDokdoRings.sortedByDescending { it.size }.take(2).all { it.size >= 30 })
        assertEquals("울릉군", district?.name)
        assertEquals(2, district?.rings?.count { it.isDokdoIslandRing() })
        assertEquals("47940", KoreanSelectableDistrictCodes.single { it.name == "경상북도 울릉군" }.code)
        assertEquals(LocationType.DISTRICT, location?.type)
    }

    private fun List<GeoPoint>.isDokdoIslandRing(): Boolean =
        size >= 3 && all { point ->
            kotlin.math.abs(point.longitude - 131.86941f) <= 0.05f &&
                kotlin.math.abs(point.latitude - 37.24006f) <= 0.05f
        }
}
