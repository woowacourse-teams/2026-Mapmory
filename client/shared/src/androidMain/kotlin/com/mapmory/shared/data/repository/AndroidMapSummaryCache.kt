package com.mapmory.shared.data.repository

import android.content.Context
import kotlinx.serialization.json.Json

class AndroidMapSummaryCache(context: Context) : MapSummaryCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): MapSummarySnapshot? = preferences.getString(SnapshotKey, null)
        ?.let { payload -> runCatching { json.decodeFromString<MapSummarySnapshot>(payload) }.getOrNull() }

    override fun write(snapshot: MapSummarySnapshot) {
        val payload = runCatching { json.encodeToString(snapshot) }.getOrNull() ?: return
        preferences.edit().putString(SnapshotKey, payload).apply()
    }

    override fun clear() {
        preferences.edit().remove(SnapshotKey).apply()
    }
}

private const val PreferencesName = "mapmory_map_summary"
private const val SnapshotKey = "latest_map_summary"
