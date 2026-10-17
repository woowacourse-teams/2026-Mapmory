package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.ui.regionAt
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GunwiMapSelectionTest {
    @Test
    fun `전국_채움과_동일한_지도에서_군위와_대구_경북을_선택한다`() = runBlocking {
        val provinces = GeneratedKoreaDistrictMapData.overviewProvinces()
        val districts = GeneratedKoreaDistrictMapData.overviewDistricts()

        assertEquals("KR-27", provinces.regionAt(GeoPoint(128.57f, 36.24f))?.code)
        assertEquals("KR-29", provinces.regionAt(GeoPoint(126.8526f, 35.1595f))?.code)
        assertEquals("27720", districts.regionAt(GeoPoint(128.57f, 36.24f))?.code)
        assertEquals("KR-27", provinces.regionAt(GeoPoint(128.60f, 35.87f))?.code)
        assertEquals("KR-47", provinces.regionAt(GeoPoint(128.69f, 36.35f))?.code)
    }
}
