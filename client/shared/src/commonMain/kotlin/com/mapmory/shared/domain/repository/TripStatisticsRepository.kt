package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.TripStatistics

interface TripStatisticsRepository {
    fun getCachedStatistics(): TripStatistics? = null

    suspend fun getStatistics(): Result<TripStatistics>
}
