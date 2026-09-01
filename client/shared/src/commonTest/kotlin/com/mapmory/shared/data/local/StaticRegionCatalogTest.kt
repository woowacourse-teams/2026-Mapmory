package com.mapmory.shared.data.local

import com.mapmory.shared.domain.model.LocationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StaticRegionCatalogTest {
    private val catalog = StaticRegionCatalog()

    @Test
    fun `App_컴포저블_없이_정규_시도와_지역_코드를_조회한다`() {
        val seoul = catalog.findByCode("KR-11")
        val gangnam = catalog.findDistrict(
            provinceCode = "KR-11",
            districtCode = "11680",
        )

        assertEquals("서울특별시", seoul?.name)
        assertEquals(LocationType.PROVINCE, seoul?.type)
        assertEquals("강남구", gangnam?.name)
        assertEquals(seoul?.id, gangnam?.parentId)
    }

    @Test
    fun `지역_조회는_요청한_시도를_벗어나지_않는다`() {
        assertNull(
            catalog.findDistrict(
                provinceCode = "KR-26",
                districtCode = "11680",
            ),
        )
    }
}
