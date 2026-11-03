package com.mapmory.shared.analytics

import kotlinx.coroutines.CancellationException

/** Action completion means the platform API returned, not that an external share was delivered. */
internal suspend fun <T> MapmoryAnalytics.videoAction(
    action: String,
    reportErrors: Boolean = true,
    block: suspend () -> T,
): T {
    emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "started"))
    return try {
        block().also {
            emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "success"))
        }
    } catch (error: CancellationException) {
        emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "cancelled"))
        throw error
    } catch (error: Exception) {
        emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "failure"))
        if (reportErrors) reportFailure("video_$action", error)
        throw error
    }
}

/** Legacy request events alongside detailed telemetry, which owns app_error reporting. */
internal suspend fun <T> MapmoryAnalytics.videoActionResult(
    action: String,
    block: suspend () -> Result<T>,
): Result<T> {
    emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "started"))
    val result = try {
        block()
    } catch (error: Exception) {
        emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf(
            "action" to action, "outcome" to if (error is CancellationException) "cancelled" else "failure",
        ))
        throw error
    }
    val error = result.exceptionOrNull()
    emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf(
        "action" to action,
        "outcome" to when (error) {
            null -> "success"
            is CancellationException -> "cancelled"
            else -> "failure"
        },
    ))
    if (error is CancellationException) throw error
    return result
}
