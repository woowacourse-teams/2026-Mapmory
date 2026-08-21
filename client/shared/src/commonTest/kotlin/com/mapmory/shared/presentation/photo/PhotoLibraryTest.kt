package com.mapmory.shared.presentation.photo

import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.domain.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoLibraryTest {
    @Test
    fun selectedPhotosAreDeduplicatedWithoutAnApplicationLimit() {
        val existing = listOf(photo("same"), photo("existing"))
        val incoming = listOf(photo("same")) + (1..20).map { photo("new-$it") }

        val merged = mergeSelectedPhotos(existing, incoming)

        assertEquals(22, merged.size)
        assertEquals(1, merged.count { it.id == "same" })
        assertEquals(listOf("same", "existing"), merged.take(2).map(SelectedPhoto::id))
    }

    @Test
    fun boundaryIncludesInteriorAndEdgeButRejectsOutsideCoordinates() {
        val region = PhotoRecommendationRegion(
            code = "test",
            rings = listOf(square(left = 0f, bottom = 0f, right = 10f, top = 10f)),
        )

        assertTrue(region.contains(latitude = 5.0, longitude = 5.0))
        assertTrue(region.contains(latitude = 5.0, longitude = 0.0))
        assertFalse(region.contains(latitude = 5.0, longitude = 10.1))
        assertFalse(region.contains(latitude = 91.0, longitude = 5.0))
    }

    @Test
    fun boundaryChecksEveryDisconnectedRing() {
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
    fun generatedSeoulBoundaryRejectsNearbyGyeonggiPhoto() {
        val seoul = GeneratedKoreaMapData.provinces.single { it.code == "KR-11" }
        val region = PhotoRecommendationRegion(seoul.code, seoul.rings)

        assertTrue(region.contains(latitude = 37.56, longitude = 126.98))
        assertFalse(region.contains(latitude = 37.40, longitude = 127.20))
    }

    @Test
    fun recommendationKeepsNewestInputOrderAndStopsAtTwelveMatches() {
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

        assertEquals((1..12).map { "inside-$it" }, selected)
    }

    @Test
    fun nonPositiveRecommendationLimitReturnsNoPhotos() {
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
