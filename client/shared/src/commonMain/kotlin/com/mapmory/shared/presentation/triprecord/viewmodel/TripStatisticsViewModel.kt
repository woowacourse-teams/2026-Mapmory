package com.mapmory.shared.presentation.triprecord.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mapmory.shared.domain.repository.TripStatisticsRepository
import com.mapmory.shared.logging.mapmoryDebugLog
import com.mapmory.shared.presentation.triprecord.state.TopLocationUiModel
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiModel
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiState

/** 전용 여행 통계 API 응답을 화면 상태로 변환한다. */
class TripStatisticsViewModel(
    private val tripStatisticsRepository: TripStatisticsRepository,
) : ViewModel() {
    private var loadGeneration = 0L
    private val cachedStatistics = tripStatisticsRepository.getCachedStatistics()
    private var visibleStatisticsRevision: Long? = cachedStatistics?.let { InitialDataRevision }

    var uiState by mutableStateOf(
        cachedStatistics
            ?.toUiState()
            ?: TripStatisticsUiState.Loading,
    )
        private set

    init {
        if (cachedStatistics != null) {
            mapmoryDebugLog(StatisticsLogTag, "cache hit")
        }
    }

    suspend fun refresh(dataRevision: Long = InitialDataRevision) {
        val generation = ++loadGeneration
        val hasVisibleStatistics = uiState is TripStatisticsUiState.Success
        val canRetainVisibleStatistics =
            hasVisibleStatistics && visibleStatisticsRevision == dataRevision
        mapmoryDebugLog(
            StatisticsLogTag,
            "refresh started generation=$generation, canRetain=$canRetainVisibleStatistics",
        )
        if (!canRetainVisibleStatistics) {
            uiState = TripStatisticsUiState.Loading
        }

        val result = tripStatisticsRepository.getStatistics()
        if (generation != loadGeneration) {
            mapmoryDebugLog(StatisticsLogTag, "refresh ignored generation=$generation")
            return
        }
        val statistics = result.getOrElse { error ->
            mapmoryDebugLog(
                StatisticsLogTag,
                "refresh failed, retained=$canRetainVisibleStatistics: ${error::class.simpleName}",
            )
            if (!canRetainVisibleStatistics) {
                uiState = TripStatisticsUiState.Error(StatisticsLoadErrorMessage)
            }
            return
        }

        val nextState = statistics.toUiState()
        uiState = nextState
        visibleStatisticsRevision = dataRevision
        mapmoryDebugLog(StatisticsLogTag, "ui state updated")
    }
}

private fun com.mapmory.shared.domain.model.TripStatistics.toUiState() =
    TripStatisticsUiState.Success(
        TripStatisticsUiModel(
            travelerName = DefaultTravelerName,
            recordCount = recordCount.toUiCount(),
            photoCount = mediaCount.toUiCount(),
            worldVisitedCount = visitedCountryCount.toUiCount(),
            koreaVisitedCount = visitedKoreaDistrictCount.toUiCount(),
            visitedCountryCodes = visitedCountryCodes.toSet(),
            topLocations = topRegions.map { region ->
                TopLocationUiModel(
                    locationName = region.name,
                    visitCount = region.recordCount.toUiCount(),
                )
            },
        ),
    )

private fun Long.toUiCount(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

private const val DefaultTravelerName = "여행자"
private const val StatisticsLogTag = "MapmoryStatistics"
private const val StatisticsLoadErrorMessage = "여행 통계를 불러오지 못했습니다."
private const val InitialDataRevision = 0L
