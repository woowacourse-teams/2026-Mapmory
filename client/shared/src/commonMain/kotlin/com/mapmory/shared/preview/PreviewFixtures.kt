package com.mapmory.shared.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.presentation.triprecord.state.TopLocationUiModel
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiModel
import com.mapmory.shared.presentation.triprecord.state.toTripRecordItemUiState

@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}

internal val previewLocations = listOf(
    Location(
        id = 1L,
        countryId = 1L,
        parentId = null,
        regionCode = "KR-11",
        name = "서울특별시",
        type = LocationType.PROVINCE,
    ),
    Location(
        id = 2L,
        countryId = 1L,
        parentId = 1L,
        regionCode = "11680",
        name = "강남구",
        type = LocationType.DISTRICT,
    ),
    Location(
        id = 3L,
        countryId = 1L,
        parentId = 1L,
        regionCode = "11650",
        name = "서초구",
        type = LocationType.DISTRICT,
    ),
)

private val previewRecords = listOf(
    TripRecordData(
        id = 101L,
        locationId = 2L,
        title = "봄날의 서울",
        content = "천천히 걸으며 발견한 서울의 새로운 모습",
        startDate = "2026-04-12",
        endDate = "2026-04-14",
        media = listOf(
            TripRecordMedia(
                id = 1001L,
                objectKey = "preview/seoul.jpg",
                sortOrder = 0,
                url = null,
            ),
        ),
        createdAt = "2026-04-15 10:30",
        updatedAt = "2026-04-15 10:30",
        thumbnailUrl = null,
    ),
    TripRecordData(
        id = 102L,
        locationId = 3L,
        title = "비 오는 서초 산책",
        content = "빗소리와 따뜻한 커피가 좋았던 오후",
        startDate = "2026-05-03",
        endDate = null,
        media = emptyList(),
        createdAt = "2026-05-03 18:20",
        updatedAt = "2026-05-03 18:20",
        thumbnailUrl = null,
    ),
)

internal val previewUiRecords: List<TripRecordItemUiState> = previewRecords.map { record ->
    record.toTripRecordItemUiState(
        locationName = previewLocations.firstOrNull { it.id == record.locationId }?.name ?: "여행지",
    )
}

internal val previewStatistics = TripStatisticsUiModel(
    travelerName = "도우너",
    recordCount = 24,
    photoCount = 138,
    worldVisitedCount = 5,
    koreaVisitedCount = 8,
    visitedCountryCodes = setOf("KR", "JP", "US", "FR", "IT"),
    topLocations = listOf(
        TopLocationUiModel("서울", 7),
        TopLocationUiModel("부산", 4),
        TopLocationUiModel("도쿄", 3),
    ),
)

internal val previewVisitedCountries = setOf("KR", "JP", "US", "FR", "IT")
internal val previewVisitedRegions = setOf("KR-11", "KR-26", "KR-27", "KR-48")
