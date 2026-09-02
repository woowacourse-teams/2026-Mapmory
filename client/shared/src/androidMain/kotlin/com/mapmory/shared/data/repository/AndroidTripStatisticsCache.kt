package com.mapmory.shared.data.repository

import android.content.Context
import com.mapmory.shared.domain.model.TripStatistics
import kotlinx.serialization.json.Json

class AndroidTripStatisticsCache(context: Context) : TripStatisticsCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): TripStatistics? = preferences.getString(StatisticsKey, null)
        ?.let { payload -> runCatching { json.decodeFromString<TripStatistics>(payload) }.getOrNull() }

    override fun write(statistics: TripStatistics) {
        val payload = runCatching { json.encodeToString(statistics) }.getOrNull() ?: return
        preferences.edit().putString(StatisticsKey, payload).apply()
    }

    override fun clear() {
        preferences.edit().remove(StatisticsKey).apply()
    }
}

private const val PreferencesName = "mapmory_statistics"
private const val StatisticsKey = "latest_statistics"
