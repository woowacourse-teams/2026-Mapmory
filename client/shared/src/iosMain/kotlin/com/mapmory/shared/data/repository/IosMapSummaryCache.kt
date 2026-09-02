package com.mapmory.shared.data.repository

import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

class IosMapSummaryCache : MapSummaryCache {
    private val preferences = NSUserDefaults.standardUserDefaults
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): MapSummarySnapshot? = preferences.stringForKey(SnapshotKey)
        ?.let { payload -> runCatching { json.decodeFromString<MapSummarySnapshot>(payload) }.getOrNull() }

    override fun write(snapshot: MapSummarySnapshot) {
        val payload = runCatching { json.encodeToString(snapshot) }.getOrNull() ?: return
        preferences.setObject(payload, forKey = SnapshotKey)
    }

    override fun clear() {
        preferences.removeObjectForKey(SnapshotKey)
    }
}

private const val SnapshotKey = "mapmory_latest_map_summary"
