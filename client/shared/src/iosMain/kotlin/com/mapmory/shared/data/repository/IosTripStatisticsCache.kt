package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.TripStatistics
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

class IosTripStatisticsCache : TripStatisticsCache {
    private val preferences = NSUserDefaults.standardUserDefaults
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): TripStatistics? = preferences.stringForKey(StatisticsKey)
        ?.let { payload -> runCatching { json.decodeFromString<TripStatistics>(payload) }.getOrNull() }

    override fun write(statistics: TripStatistics) {
        val payload = runCatching { json.encodeToString(statistics) }.getOrNull() ?: return
        preferences.setObject(payload, forKey = StatisticsKey)
    }

    override fun clear() {
        preferences.removeObjectForKey(StatisticsKey)
    }
}

private const val StatisticsKey = "mapmory_latest_statistics"
