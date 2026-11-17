package com.mapmory.android.analytics

/** Same export contract as iOS CrashlyticsEventContext; no Android/Firebase APIs needed for tests. */
internal data class CrashlyticsEventContext(val keys: Map<String, String>, val log: String) {
    companion object {
        private val dimensions = listOf(
            "action", "error_category", "error_type", "feature", "media_type", "mode",
            "operation", "origin", "outcome", "owner_hint", "share_type", "source",
            "stage", "status", "step_name",
        )
        private val measurements = listOf(
            "count", "duration_ms", "elapsed_ms", "http_status", "photo_count",
            "platform_code", "platform_extra_code", "result_count", "selected_count", "selection_index",
        )
        private val identifierPattern = Regex("[A-Za-z_][A-Za-z0-9_.-]*")

        fun make(name: String, parameters: Map<String, String>): CrashlyticsEventContext? {
            val event = identifier(name, limit = 64) ?: return null
            val details = buildMap {
                dimensions.forEach { key -> identifier(parameters[key])?.let { put(key, it) } }
                measurements.forEach { key ->
                    parameters[key]?.toLongOrNull()?.takeIf {
                        it >= 0 || key == "platform_code" || key == "platform_extra_code"
                    }?.let { put(key, it.toString()) }
                }
            }.toMutableMap()
            val keys = mutableMapOf(
                "event_schema_version" to "2", "crash_context_schema_version" to "1", "last_event" to event,
            )
            // All last_* keys belong to one event, including explicit clearing of absent values.
            (dimensions + measurements).forEach { keys["last_$it"] = details[it] ?: "unset" }
            if (event == "app_screen_view") {
                val screen = identifier(parameters["screen_name"]) ?: "unknown"
                keys["screen_name"] = screen
                details["screen_name"] = screen
            }
            val fields = details.toSortedMap().map { (key, value) -> "$key=$value" }
            return CrashlyticsEventContext(keys, (listOf(event) + fields).joinToString(" ").take(1024))
        }

        fun teamValue(value: String?): String = when (value) {
            "true", "false" -> value
            else -> "unknown"
        }

        private fun identifier(value: String?, limit: Int = 80): String? =
            value?.takeIf { it.length in 1..limit && identifierPattern.matches(it) }
    }
}
