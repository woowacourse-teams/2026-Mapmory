package com.mapmory.shared.presentation.photo

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class PhotoRecommendationRegionResourceTest {
    @Test
    fun `모든_선택_가능한_국내_도시의_경계를_전수_검사한다`() = runBlocking {
        assertAllSelectableKoreanCityBoundaries()
    }

    @Test
    fun `제주와_세종은_명시된_상위_시도_경계를_사용한다`() = runBlocking {
        assertJejuAndSejongBoundaries()
    }
}
