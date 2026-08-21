package com.mapmory.shared.presentation.triprecord.state

sealed interface TripStatisticsUiState {
    data object Loading : TripStatisticsUiState
    data class Success(val statistics: TripStatisticsUiModel) : TripStatisticsUiState
    data class Error(val message: String) : TripStatisticsUiState
}

/** 서버 통계 응답을 화면이 바로 렌더링할 수 있는 형태로 변환한 모델. */
data class TripStatisticsUiModel(
    val travelerName: String,
    val recordCount: Int,
    val photoCount: Int,
    val worldVisitedCount: Int,
    val koreaVisitedCount: Int,
    val visitedCountryCodes: Set<String>,
    val topLocations: List<TopLocationUiModel>,
) {
    companion object {
        val Empty = TripStatisticsUiModel(
            travelerName = "여행자",
            recordCount = 0,
            photoCount = 0,
            worldVisitedCount = 0,
            koreaVisitedCount = 0,
            visitedCountryCodes = emptySet(),
            topLocations = emptyList(),
        )
    }
}

data class TopLocationUiModel(
    val locationName: String,
    val visitCount: Int,
)
