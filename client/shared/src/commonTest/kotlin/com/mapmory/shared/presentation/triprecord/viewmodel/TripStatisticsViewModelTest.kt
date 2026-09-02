package com.mapmory.shared.presentation.triprecord.viewmodel

import com.mapmory.shared.domain.model.MapRegionType
import com.mapmory.shared.domain.model.TopRegionStatistics
import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.domain.repository.TripStatisticsRepository
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiState
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TripStatisticsViewModelTest {
    @Test
    fun `전용_통계_API_응답을_현재_화면_상태로_변환한다`() = runSuspend {
        val repository = StubTripStatisticsRepository(
            responses = mutableListOf(
                Result.success(statistics(recordCount = 5, mediaCount = 9)),
            ),
        )
        val viewModel = TripStatisticsViewModel(repository)

        viewModel.refresh()

        val statistics = assertIs<TripStatisticsUiState.Success>(viewModel.uiState).statistics
        assertEquals(5, statistics.recordCount)
        assertEquals(9, statistics.photoCount)
        assertEquals(2, statistics.worldVisitedCount)
        assertEquals(2, statistics.koreaVisitedCount)
        assertEquals(setOf("JP", "KR"), statistics.visitedCountryCodes)
        assertEquals(listOf("서울특별시", "일본"), statistics.topLocations.map { it.locationName })
        assertEquals(listOf(3, 2), statistics.topLocations.map { it.visitCount })
    }

    @Test
    fun `다시_조회하면_변경된_서버_통계로_화면_상태를_교체한다`() = runSuspend {
        val repository = StubTripStatisticsRepository(
            responses = mutableListOf(
                Result.success(statistics(recordCount = 1, mediaCount = 2)),
                Result.success(statistics(recordCount = 2, mediaCount = 5)),
            ),
        )
        val viewModel = TripStatisticsViewModel(repository)

        viewModel.refresh()
        assertEquals(
            1,
            assertIs<TripStatisticsUiState.Success>(viewModel.uiState).statistics.recordCount,
        )

        viewModel.refresh()

        val updated = assertIs<TripStatisticsUiState.Success>(viewModel.uiState).statistics
        assertEquals(2, updated.recordCount)
        assertEquals(5, updated.photoCount)
        assertEquals(2, repository.requestCount)
    }

    @Test
    fun `같은_기록_리비전의_캐시된_통계는_백그라운드_갱신_실패에도_유지한다`() = runSuspend {
        val repository = StubTripStatisticsRepository(
            responses = mutableListOf(Result.failure(IllegalStateException("일시적 오류"))),
            cached = statistics(recordCount = 4, mediaCount = 7),
        )
        val viewModel = TripStatisticsViewModel(repository)

        val cachedState = assertIs<TripStatisticsUiState.Success>(viewModel.uiState).statistics
        assertEquals(4, cachedState.recordCount)
        assertEquals(7, cachedState.photoCount)

        viewModel.refresh()

        val retainedState = assertIs<TripStatisticsUiState.Success>(viewModel.uiState).statistics
        assertEquals(4, retainedState.recordCount)
        assertEquals(7, retainedState.photoCount)
    }

    @Test
    fun `기록_변경_후_통계_재조회가_실패하면_이전_통계를_노출하지_않는다`() = runSuspend {
        val repository = StubTripStatisticsRepository(
            responses = mutableListOf(
                Result.success(statistics(recordCount = 4, mediaCount = 7)),
                Result.failure(IllegalStateException("서버 연결 실패")),
            ),
        )
        val viewModel = TripStatisticsViewModel(repository)
        viewModel.refresh(dataRevision = 0)

        viewModel.refresh(dataRevision = 1)

        assertEquals(
            TripStatisticsUiState.Error("여행 통계를 불러오지 못했습니다."),
            viewModel.uiState,
        )
    }

    @Test
    fun `통계_API_조회가_실패하면_오류_상태를_노출한다`() = runSuspend {
        val viewModel = TripStatisticsViewModel(
            StubTripStatisticsRepository(
                mutableListOf(Result.failure(IllegalStateException("서버 연결 실패"))),
            ),
        )

        viewModel.refresh()

        assertEquals(
            TripStatisticsUiState.Error("여행 통계를 불러오지 못했습니다."),
            viewModel.uiState,
        )
    }
}

private class StubTripStatisticsRepository(
    private val responses: MutableList<Result<TripStatistics>>,
    private val cached: TripStatistics? = null,
) : TripStatisticsRepository {
    var requestCount: Int = 0
        private set

    override fun getCachedStatistics(): TripStatistics? = cached

    override suspend fun getStatistics(): Result<TripStatistics> {
        requestCount += 1
        return responses.removeAt(0)
    }
}

private fun statistics(recordCount: Long, mediaCount: Long) = TripStatistics(
    recordCount = recordCount,
    mediaCount = mediaCount,
    visitedCountryCount = 2,
    visitedKoreaDistrictCount = 2,
    visitedCountryCodes = listOf("JP", "KR"),
    topRegions = listOf(
        TopRegionStatistics(11, "11", MapRegionType.PROVINCE, "서울특별시", 3),
        TopRegionStatistics(2, "JP", MapRegionType.COUNTRY, "일본", 2),
    ),
)
