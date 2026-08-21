package com.mapmory.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.presentation.triprecord.screen.TripMapArtwork
import com.mapmory.shared.presentation.triprecord.screen.TripMapScreen
import com.mapmory.shared.presentation.triprecord.screen.TripProfileScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordDetailScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordEditorScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordListScreen
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordFilterUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.state.TopLocationUiModel
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiModel
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiState
import com.mapmory.shared.presentation.triprecord.state.toTripRecordItemUiState

@Preview(
    name = "지도",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun MapmoryAppPreview() {
    MapmoryApp()
}

@Preview(
    name = "여행 기록 목록",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordListScreenPreview() {
    PreviewTheme {
        TripRecordListScreen(
            uiState = TripRecordListUiState.Success(
                records = previewUiRecords,
                page = 0,
                totalPages = 3,
            ),
            filter = TripRecordFilterUiState(),
            locations = previewLocations,
            onKeywordChanged = {},
            onLocationChanged = {},
            onSearchClick = {},
            onPreviousPageClick = {},
            onNextPageClick = {},
            onCreateClick = {},
            onMapClick = {},
            onRecordClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 상세",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordDetailScreenPreview() {
    PreviewTheme {
        TripRecordDetailScreen(
            uiState = TripRecordDetailUiState.Success(previewUiRecords.first()),
            onBackClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 작성",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordEditorScreenPreview() {
    PreviewTheme {
        TripRecordEditorScreen(
            uiState = TripRecordEditorUiState(
                selectedLocation = previewLocations[1],
                title = "봄날의 서울",
                content = "천천히 걸으며 발견한 서울의 새로운 모습",
                startDate = "2026-04-12",
                endDate = "2026-04-14",
            ),
            locations = previewLocations,
            onLocationSelected = {},
            onTitleChanged = {},
            onContentChanged = {},
            onStartDateChanged = {},
            onEndDateChanged = {},
            onSaveClick = {},
            onBackClick = {},
        )
    }
}

@Preview(
    name = "지도",
    showBackground = true,
    widthDp = 412,
    heightDp = 760,
)
@Composable
fun TripMapScreenPreview() {
    PreviewTheme {
        TripMapScreen(
            mapContent = { TripMapArtwork() },
            onBackClick = {},
        )
    }
}

@Preview(
    name = "여행 통계",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripStatisticsScreenPreview() {
    PreviewTheme {
        TripProfileScreen(
            statisticsUiState = TripStatisticsUiState.Success(
                TripStatisticsUiModel(
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
                ),
            ),
            onMapClick = {},
            onRecordClick = {},
            onCreateClick = {},
            onProfileClick = {},
        )
    }
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}

private val previewLocations = listOf(
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
        memberId = 7L,
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
        memberId = 7L,
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

private val previewUiRecords = previewRecords.map { record ->
    record.toTripRecordItemUiState(
        locationName = previewLocations.firstOrNull { it.id == record.locationId }?.name ?: "여행지",
    )
}
