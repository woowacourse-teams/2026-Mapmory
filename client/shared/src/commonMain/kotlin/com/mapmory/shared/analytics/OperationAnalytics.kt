package com.mapmory.shared.analytics

import com.mapmory.shared.data.remote.MapmoryConnectionException
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.presentation.triprecord.share.ShareVideoPlaybackException
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlin.time.TimeSource

/** Analytics must never turn a successful business operation into a failure/retry. */
internal fun MapmoryAnalytics.emit(name: String, parameters: Map<String, String> = emptyMap()) {
    try {
        logEvent(name, parameters + ("event_schema_version" to "2"))
    } catch (_: Exception) {
        // Reporting is best effort; never include exception messages or user content.
    }
}

internal fun MapmoryAnalytics.reportFailure(
    operation: String, error: Throwable, context: Map<String, String> = emptyMap(),
) {
    if (error is CancellationException) return
    val category = when (error) {
        is MapmoryApiException -> when (error.statusCode) {
            401, 403 -> "authentication"
            429 -> "rate_limit"
            in 500..599 -> "server"
            else -> "request_rejected"
        }
        is MapmoryConnectionException -> "io"
        is SerializationException -> "data_contract"
        is IOException -> "io"
        is ShareVideoPlaybackException -> "media_playback"
        is IllegalArgumentException, is IllegalStateException -> "client_logic"
        else -> "unknown"
    }
    emit(MapmoryAnalyticsEvent.APP_ERROR, buildMap {
        putAll(context)
        put("operation", operation)
        put("error_category", category)
        // A triage hint, not a claim that responsibility has been established.
        put("owner_hint", when (category) {
            "client_logic" -> "mobile"
            "server" -> "server"
            "data_contract" -> "shared_contract"
            else -> "undetermined"
        })
        put("error_type", (error::class.simpleName ?: "unknown").take(100))
        (error as? MapmoryApiException)?.let { put("http_status", it.statusCode.toString()) }
    })
}

internal suspend fun <T> MapmoryAnalytics.trackAction(
    eventName: String,
    operation: String,
    action: String,
    reportErrors: Boolean = true,
    block: suspend () -> T,
): T {
    emit(eventName, mapOf("action" to action, "outcome" to "started"))
    return try {
        block().also {
            emit(eventName, mapOf("action" to action, "outcome" to "success"))
        }
    } catch (error: CancellationException) {
        emit(eventName, mapOf("action" to action, "outcome" to "cancelled"))
        throw error
    } catch (error: Exception) {
        emit(eventName, mapOf("action" to action, "outcome" to "failure"))
        if (reportErrors) reportFailure(operation, error)
        throw error
    }
}

/** One event per repository invocation; retries are separate attempts. Cancellation is excluded. */
internal suspend fun <T> MapmoryAnalytics.observeOperation(
    operation: String,
    block: suspend () -> Result<T>,
): Result<T> {
    val started = TimeSource.Monotonic.markNow()
    fun report(result: Result<T>) {
        val error = result.exceptionOrNull()
        if (error is CancellationException) throw error
        emit(MapmoryAnalyticsEvent.OPERATION_COMPLETED, mapOf(
            "operation" to operation,
            "outcome" to if (result.isSuccess) "success" else "failure",
            "duration_ms" to started.elapsedNow().inWholeMilliseconds.toString(),
        ))
        error?.let { reportFailure(operation, it) }
    }
    val result = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        report(Result.failure(error))
        throw error
    }
    report(result)
    return result
}
