package com.mapmory.shared.analytics

import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.presentation.triprecord.share.ShareMediaPermissionDeniedException
import com.mapmory.shared.presentation.triprecord.share.ShareVideoPlaybackException
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

internal enum class ShareMediaSource(val value: String, val mediaType: String) {
    TRAVEL_TIMELINE("travel_timeline", "video"), RECORD("record", "image"),
    STATISTICS("statistics", "image"), ACHIEVEMENT("achievement", "image"),
}

internal enum class ShareMediaAction {
    OPENED, CLOSED, CREATE_CLICKED, REGENERATE_CLICKED, STATUS_RETRY_CLICKED,
    DOWNLOAD_RETRY_CLICKED, SAVE_CLICKED, SHARE_CLICKED,
    DESIGN_VIEWED, PHOTO_SELECTED, PHOTO_RETRY_CLICKED, EXPORT_BLOCKED_LOADING, EXPORT_BLOCKED_FAILED,
    PREVIEW_STARTED, PREVIEW_PAUSED, PREVIEW_COMPLETED,
}

internal enum class ShareMediaOperation {
    CREATE_REQUEST, GENERATION, DOWNLOAD_URL, DOWNLOAD, IMAGE_CAPTURE, IMAGE_LOAD,
    SAVE, SHARE_SHEET, STATUS_CHECK, PREVIEW_LOAD, PREVIEW_PLAYBACK,
    NOTIFICATION_PERMISSION, NOTIFICATION_PROGRESS, NOTIFICATION_CLEAR, NOTIFICATION_READY,
}

/** Fixed vocabulary only: do not pass content, IDs, paths, URLs or arbitrary SDK messages. */
internal class ShareMediaAnalytics(
    private val analytics: MapmoryAnalytics,
    private val source: ShareMediaSource,
) {
    internal val context: Map<String, String> get() = mapOf(
        "feature" to "media_share", "share_type" to source.value, "media_type" to source.mediaType,
    )

    fun action(action: ShareMediaAction, index: Int? = null) {
        analytics.emit(MapmoryAnalyticsEvent.SHARE_MEDIA_ACTION, context + buildMap {
            put("action", action.name.lowercase())
            index?.let { put("selection_index", it.toString()) }
        })
    }

    fun start(operation: ShareMediaOperation): Attempt = Attempt(operation).also {
        analytics.emit(MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_STARTED, context +
            ("operation" to operation.name.lowercase()))
    }

    inner class Attempt internal constructor(private val operation: ShareMediaOperation) {
        private val started = TimeSource.Monotonic.markNow()
        private var finished = false
        fun complete(outcome: String = "success") {
            if (finished) return
            finished = true
            analytics.emit(MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED, context + mapOf(
                "operation" to operation.name.lowercase(), "outcome" to outcome,
                "duration_ms" to started.elapsedNow().inWholeMilliseconds.toString(),
            ))
        }
        fun failed(error: Throwable) {
            if (finished) return
            complete(shareMediaOutcome(error))
            reportError(operation, error)
        }
    }

    suspend fun <T> observe(operation: ShareMediaOperation, block: suspend () -> T): T {
        val attempt = start(operation)
        return try {
            block().also { attempt.complete(if (operation == ShareMediaOperation.SHARE_SHEET) "handoff" else "success") }
        } catch (error: Exception) {
            attempt.failed(error)
            throw error
        }
    }

    suspend fun <T> observeResult(operation: ShareMediaOperation, block: suspend () -> Result<T>): Result<T> {
        val attempt = start(operation)
        val result = try { block() } catch (error: Exception) {
            attempt.failed(error)
            throw error
        }
        result.fold(onSuccess = { attempt.complete() }, onFailure = {
            attempt.failed(it)
            if (it is CancellationException) throw it
        })
        return result
    }

    /**
     * Record an observed failure without a measured attempt (no started event or duration).
     * Used for status-query error episodes, server generation, playback after readiness,
     * and notification permission/progress/clear failures. Successful observations are silent;
     * callers own any deduplication. Use start/observe when measuring a whole attempt.
     */
    fun recordObservedFailure(operation: ShareMediaOperation, error: Throwable) {
        if (error is CancellationException) return
        analytics.emit(MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED, context + mapOf(
            "operation" to operation.name.lowercase(), "outcome" to shareMediaOutcome(error),
        ))
        reportError(operation, error)
    }

    fun imageLoadFailed() {
        // MapmoryAsyncImage already owns the actual photo_display exception report.
        analytics.emit(MapmoryAnalyticsEvent.SHARE_MEDIA_OPERATION_COMPLETED, context + mapOf(
            "operation" to "image_load", "outcome" to "failure",
        ))
    }

    private fun reportError(operation: ShareMediaOperation, error: Throwable) {
        if (shareMediaOutcome(error) != "failure") return
        analytics.reportFailure("share_${source.mediaType}_${operation.name.lowercase()}", error, context + buildMap {
            put("stage", operation.name.lowercase())
            (error as? ShareVideoPlaybackException)?.let {
                put("platform_code", it.platformCode.toString())
                it.platformExtraCode?.let { code -> put("platform_extra_code", code.toString()) }
            }
        })
    }
}

private fun shareMediaOutcome(error: Throwable): String = when {
    error is CancellationException -> "cancelled"
    error is ShareMediaPermissionDeniedException -> "permission_denied"
    error is MapmoryApiException && error.code == "SHARE_VIDEO_NOT_READY" -> "not_ready"
    error is MapmoryApiException && error.code == "INSUFFICIENT_TIMELINE_STOPS" -> "insufficient_records"
    else -> "failure"
}

/** Instance lifetime is one native preview, so recomposition cannot duplicate ready/error events. */
internal class ShareVideoPreviewAnalytics(analytics: MapmoryAnalytics) {
    private val telemetry = ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE)
    private var loading: ShareMediaAnalytics.Attempt? = null
    private var ready = false
    private var failed = false
    private var playing = false
    private var ended = false
    private var disposed = false
    fun opened() { if (!disposed && loading == null) loading = telemetry.start(ShareMediaOperation.PREVIEW_LOAD) }
    fun ready() {
        if (disposed || ready || failed) return
        opened(); ready = true; loading?.complete()
    }
    fun playbackChanged(isPlaying: Boolean) {
        if (disposed || !ready || failed || isPlaying == playing) return
        playing = isPlaying
        if (isPlaying) ended = false
        telemetry.action(if (isPlaying) ShareMediaAction.PREVIEW_STARTED else ShareMediaAction.PREVIEW_PAUSED)
    }
    fun completed() {
        if (disposed || !ready || failed || ended) return
        ended = true; playing = false
        telemetry.action(ShareMediaAction.PREVIEW_COMPLETED)
    }
    fun failed(error: Throwable) {
        if (disposed || failed) return
        failed = true; opened()
        if (ready) telemetry.recordObservedFailure(ShareMediaOperation.PREVIEW_PLAYBACK, error) else loading?.failed(error)
    }
    fun closed() {
        disposed = true
        if (!ready && !failed) loading?.complete("cancelled")
    }
}
