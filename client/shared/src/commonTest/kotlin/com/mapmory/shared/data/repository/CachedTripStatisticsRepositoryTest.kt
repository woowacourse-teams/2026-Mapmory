package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.domain.repository.TripStatisticsRepository
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedTripStatisticsRepositoryTest {
    @Test
    fun `성공한_통계를_캐시하고_기록_변경_시_무효화한다`() = runSuspend {
        val expected = statistics(recordCount = 3)
        val repository = CachedTripStatisticsRepository(
            delegate = StaticTripStatisticsRepository(expected),
            cache = MemoryTripStatisticsCache(),
        )

        assertEquals(null, repository.getCachedStatistics())

        repository.getStatistics().getOrThrow()

        assertEquals(expected, repository.getCachedStatistics())

        repository.invalidate()

        assertEquals(null, repository.getCachedStatistics())
    }
}

private class StaticTripStatisticsRepository(
    private val statistics: TripStatistics,
) : TripStatisticsRepository {
    override suspend fun getStatistics(): Result<TripStatistics> = Result.success(statistics)
}

private fun statistics(recordCount: Long) = TripStatistics(
    recordCount = recordCount,
    mediaCount = 0,
    visitedCountryCount = 0,
    visitedKoreaDistrictCount = 0,
    visitedCountryCodes = emptyList(),
    topRegions = emptyList(),
)
