package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripRecordDestinationTest {
    @Test
    fun `국내는_시군구만_여행지_선택지에_포함한다`() {
        val seoul = location(1, 1, "KR-11", "서울특별시", LocationType.PROVINCE)
        val gangnam = location(2, 1, "11680", "강남구", LocationType.DISTRICT)
        val korea = location(3, 3, "KR", "대한민국", LocationType.PROVINCE)

        assertFalse(seoul.isSelectableTripRecordDestination())
        assertTrue(gangnam.isSelectableTripRecordDestination())
        assertFalse(korea.isSelectableTripRecordDestination())
    }

    @Test
    fun `해외는_국가를_여행지_선택지에_포함한다`() {
        val japan = location(100, 100, "JP", "일본", LocationType.PROVINCE)

        assertTrue(japan.isSelectableTripRecordDestination())
    }

    @Test
    fun `여행지_목록은_서버가_저장할_수_있는_단계만_남기고_코드_중복을_제거한다`() {
        val gangnam = location(2, 1, "11680", "강남구", LocationType.DISTRICT)
        val locations = listOf(
            location(1, 1, "KR-11", "서울특별시", LocationType.PROVINCE),
            gangnam,
            gangnam.copy(id = 20),
            location(3, 3, "KR", "대한민국", LocationType.PROVINCE),
            location(100, 100, "JP", "일본", LocationType.PROVINCE),
        )

        assertEquals(
            listOf("11680", "JP"),
            locations.selectableTripRecordDestinations().map(Location::regionCode),
        )
    }

    private fun location(
        id: Long,
        countryId: Long,
        code: String,
        name: String,
        type: LocationType,
    ) = Location(
        id = id,
        countryId = countryId,
        parentId = null,
        regionCode = code,
        name = name,
        type = type,
    )
}
