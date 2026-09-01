package com.mapmory.shared.presentation.photo

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class PhotoRecommendationRegionDeviceTest {
    @Test
    fun `정규_지역_코드로_번들된_지도_경계를_조회한다`() = runBlocking {
        val gangnam = Location(
            id = 11680L,
            countryId = 1L,
            parentId = 1L,
            regionCode = "11680",
            name = "강남구",
            type = LocationType.DISTRICT,
        )

        assertEquals("11680", gangnam.photoRecommendationRegion().code)
    }

    @Test
    fun `제주와_세종은_명시된_상위_시도_경계를_조회한다`() = runBlocking {
        val locations = listOf(
            Location(50110L, 1L, 19L, "50110", "제주시", LocationType.DISTRICT),
            Location(50130L, 1L, 19L, "50130", "서귀포시", LocationType.DISTRICT),
            Location(36110L, 1L, 10L, "36110", "세종시", LocationType.DISTRICT),
        )

        locations.forEach { location ->
            assertEquals(location.regionCode, location.photoRecommendationRegion().code)
        }
    }

    @Test
    fun `알_수_없는_지역_코드는_다른_지도_경계를_사용하지_않는다`() = runBlocking {
        val unknownDistrict = Location(
            id = 11999L,
            countryId = 1L,
            parentId = 1L,
            regionCode = "11999",
            name = "알 수 없는 구",
            type = LocationType.DISTRICT,
        )

        assertFailsWith<PhotoRecommendationRegionNotFoundException> {
            unknownDistrict.photoRecommendationRegion()
        }
        Unit
    }
}
