package com.mapmory.shared.presentation.photo

import com.mapmory.shared.data.local.StaticRegionCatalog
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue
import kotlin.test.Test

class PhotoRecommendationRegionResourceTest {
    @Test
    fun `모든_선택_가능한_국내_도시의_경계를_전수_검사한다`() = runBlocking {
        assertAllSelectableKoreanCityBoundaries()
    }

    @Test
    fun `특별시_광역시_세종_제주의_사진_추천은_도시_전체_경계를_사용한다`() = runBlocking {
        val catalog = StaticRegionCatalog()
        val cityCenters = listOf(
            Triple("11", 37.5665, 126.9780),
            Triple("26", 35.1796, 129.0756),
            Triple("27", 35.8714, 128.6014),
            Triple("28", 37.4563, 126.7052),
            Triple("29", 35.1595, 126.8526),
            Triple("30", 36.3504, 127.3845),
            Triple("31", 35.5384, 129.3114),
            Triple("50", 36.4800, 127.2890),
            Triple("49", 33.4996, 126.5312),
        )
        cityCenters.forEach { (code, latitude, longitude) ->
            val region = catalog.requireByCode("KR-$code").photoRecommendationRegion()
            assertTrue(region.contains(latitude, longitude), "KR-$code 도시 중심이 경계에 포함되어야 한다")
        }
    }

    @Test
    fun `제주와_세종은_명시된_상위_시도_경계를_사용한다`() = runBlocking {
        assertJejuAndSejongBoundaries()
    }
}
