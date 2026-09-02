package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.domain.repository.TripStatisticsRepository

interface TripStatisticsCache {
    fun read(): TripStatistics?

    fun write(statistics: TripStatistics)

    fun clear()
}

class MemoryTripStatisticsCache : TripStatisticsCache {
    private var statistics: TripStatistics? = null

    override fun read(): TripStatistics? = statistics

    override fun write(statistics: TripStatistics) {
        this.statistics = statistics
    }

    override fun clear() {
        statistics = null
    }
}

internal class CachedTripStatisticsRepository(
    private val delegate: TripStatisticsRepository,
    private val cache: TripStatisticsCache,
) : TripStatisticsRepository {
    override fun getCachedStatistics(): TripStatistics? = cache.read()

    override suspend fun getStatistics(): Result<TripStatistics> =
        delegate.getStatistics().onSuccess { statistics ->
            runCatching { cache.write(statistics) }
        }

    fun invalidate() {
        runCatching(cache::clear)
    }
}
