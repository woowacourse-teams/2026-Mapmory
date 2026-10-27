package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class TripRecordPhotoCollectionTest {
    @Test
    fun `지역별로 기록을 묶고 사진 수를 합산한다`() {
        val collections = groupTripRecordsByLocation(
            listOf(
                record(id = 1, location = "중국", photoCount = 8),
                record(id = 2, location = "일본", photoCount = 4),
                record(id = 3, location = "중국", photoCount = 6),
            ),
        )

        assertEquals(listOf("중국", "일본"), collections.map { it.locationName })
        assertEquals(listOf(1L, 3L), collections.first().records.map { it.id })
        assertEquals(14, collections.first().photoCount)
    }

    @Test
    fun `사진 스트립은 기록 순서와 사진 촬영 순서를 유지한다`() {
        val collection = TripRecordPhotoCollection(
            locationName = "서울",
            records = listOf(
                record(
                    id = 1,
                    location = "서울",
                    photoCount = 2,
                    photos = listOf(photo("late", 2), photo("early", 1)),
                ),
                record(id = 2, location = "서울", photoCount = 1, photos = listOf(photo("next", 0))),
            ),
        )

        assertEquals(
            listOf("early", "late", "next"),
            collection.photoEntries.map { it.second.id },
        )
    }

    @Test
    fun `첫 여행지는 대표 카드로 남기고 나머지는 두 칸씩 묶는다`() {
        val collections = (1..4).map { index ->
            TripRecordPhotoCollection("지역 $index", emptyList())
        }

        assertEquals(
            listOf(listOf("지역 2", "지역 3"), listOf("지역 4")),
            groupSecondaryTripRecordCollections(collections).map { row ->
                row.map(TripRecordPhotoCollection::locationName)
            },
        )
    }

    private fun record(
        id: Long,
        location: String,
        photoCount: Int,
        photos: List<TripRecordPhotoUiState> = emptyList(),
    ) = TripRecordItemUiState(
        id = id,
        title = "여행 $id",
        content = "",
        startDate = "2026-10-0$id",
        endDate = null,
        locationName = location,
        photos = photos,
        photoCount = photoCount,
    )

    private fun photo(id: String, sortOrder: Int) = TripRecordPhotoUiState(
        id = id,
        displayName = id,
        previewBytes = null,
        sortOrder = sortOrder,
    )
}
