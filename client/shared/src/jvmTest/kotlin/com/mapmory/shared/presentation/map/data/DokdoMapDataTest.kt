package com.mapmory.shared.presentation.map.data

import com.mapmory.shared.domain.model.KoreanSelectableDistrictCodes
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.ui.regionAt
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DokdoMapDataTest {
    @Test
    fun `독도_좌표는_기존_울릉군_행정구역으로_선택된다`() = runBlocking {
        val districts = GeneratedKoreaDistrictMapData.forProvince("KR-47")

        assertEquals("울릉군", districts.regionAt(GeoPoint(131.86941f, 37.24006f))?.name)
        assertEquals("47940", KoreanSelectableDistrictCodes.single { it.name == "경상북도 울릉군" }.code)
    }
}
