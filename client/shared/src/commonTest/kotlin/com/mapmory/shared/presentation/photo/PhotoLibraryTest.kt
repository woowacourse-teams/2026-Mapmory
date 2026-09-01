package com.mapmory.shared.presentation.photo

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.domain.GeoPoint
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoLibraryTest {
    @Test
    fun `선택한_사진은_앱_제한_없이_중복_제거된다`() {
        val existing = listOf(photo("same"), photo("existing"))
        val incoming = listOf(photo("same")) + (1..20).map { photo("new-$it") }

        val merged = mergeSelectedPhotos(existing, incoming)

        assertEquals(22, merged.size)
        assertEquals(1, merged.count { it.id == "same" })
        assertEquals(listOf("same", "existing"), merged.take(2).map(SelectedPhoto::id))
    }

    @Test
    fun `경계는_내부와_선_위_좌표를_포함하고_외부_좌표는_거부한다`() {
        val region = PhotoRecommendationRegion(
            code = "test",
            rings = listOf(square(left = 0f, bottom = 0f, right = 10f, top = 10f)),
        )

        assertTrue(region.contains(latitude = 5.0, longitude = 5.0))
        assertTrue(region.contains(latitude = 5.0, longitude = 0.0))
        assertFalse(region.contains(latitude = 5.0, longitude = 10.1))
        assertFalse(region.contains(latitude = 91.0, longitude = 5.0))
        assertFalse(region.contains(latitude = 5.0, longitude = 181.0))
    }

    @Test
    fun `퇴화한_링은_어떤_사진과도_일치하지_않는다`() {
        val region = PhotoRecommendationRegion(
            code = "invalid",
            rings = listOf(
                emptyList(),
                listOf(GeoPoint(0f, 0f), GeoPoint(1f, 1f)),
            ),
        )

        assertFalse(region.contains(latitude = 0.5, longitude = 0.5))
    }

    @Test
    fun `경계는_분리된_모든_링을_확인한다`() {
        val region = PhotoRecommendationRegion(
            code = "islands",
            rings = listOf(
                square(left = 0f, bottom = 0f, right = 2f, top = 2f),
                square(left = 10f, bottom = 10f, right = 12f, top = 12f),
            ),
        )

        assertTrue(region.contains(latitude = 1.0, longitude = 1.0))
        assertTrue(region.contains(latitude = 11.0, longitude = 11.0))
        assertFalse(region.contains(latitude = 5.0, longitude = 5.0))
    }

    @Test
    fun `생성된_서울_경계는_인접한_경기도_사진을_거부한다`() {
        val seoul = GeneratedKoreaMapData.provinces.single { it.code == "KR-11" }
        val region = PhotoRecommendationRegion(seoul.code, seoul.rings)

        assertTrue(region.contains(latitude = 37.56, longitude = 126.98))
        assertFalse(region.contains(latitude = 37.40, longitude = 127.20))
    }

    @Test
    fun `경계가_없는_지역은_빈_추천이_아니라_예외를_발생시킨다`() = runBlocking {
        val unknown = Location(
            id = -1,
            countryId = 1,
            parentId = null,
            regionCode = "99999",
            name = "알 수 없는 지역",
            type = LocationType.DISTRICT,
        )

        assertFailsWith<PhotoRecommendationRegionNotFoundException> {
            unknown.photoRecommendationRegion()
        }
        Unit
    }

    @Test
    fun `추천_결과는_최신_입력_순서를_유지하고_열두_개에서_멈춘다`() {
        val region = PhotoRecommendationRegion(
            code = "test",
            rings = listOf(square(left = 0f, bottom = 0f, right = 10f, top = 10f)),
        )
        val candidates = sequence {
            yield(LocatedPhoto("outside-newest", latitude = 20.0, longitude = 20.0))
            for (index in 1..20) {
                yield(LocatedPhoto("inside-$index", latitude = 5.0, longitude = 5.0))
            }
        }

        val selected = selectPhotosInRegion(candidates, region)

        assertEquals((1..20).map { "inside-$it" }, selected)
    }

    @Test
    fun `양수가_아닌_추천_개수_제한은_사진을_반환하지_않는다`() {
        val region = PhotoRecommendationRegion(
            code = "test",
            rings = listOf(square(left = 0f, bottom = 0f, right = 10f, top = 10f)),
        )

        assertEquals(
            emptyList(),
            selectPhotosInRegion(
                candidatesNewestFirst = sequenceOf(
                    LocatedPhoto("inside", latitude = 5.0, longitude = 5.0),
                ),
                region = region,
                limit = 0,
            ),
        )
    }

    private fun square(
        left: Float,
        bottom: Float,
        right: Float,
        top: Float,
    ): List<GeoPoint> = listOf(
        GeoPoint(left, bottom),
        GeoPoint(right, bottom),
        GeoPoint(right, top),
        GeoPoint(left, top),
    )

    private fun photo(id: String) = SelectedPhoto(
        id = id,
        displayName = "$id.jpg",
        previewBytes = null,
    )
}
