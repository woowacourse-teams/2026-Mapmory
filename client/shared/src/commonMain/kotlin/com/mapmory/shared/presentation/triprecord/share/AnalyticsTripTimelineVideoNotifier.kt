package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.ShareMediaAnalytics
import com.mapmory.shared.analytics.ShareMediaOperation
import com.mapmory.shared.analytics.ShareMediaSource

@Composable
internal fun rememberAnalyticsTripTimelineVideoNotifier(): TripTimelineVideoNotifier {
    val delegate = rememberTripTimelineVideoNotifier()
    val analytics = LocalMapmoryAnalytics.current
    return remember(delegate, analytics) {
        AnalyticsTripTimelineVideoNotifier(delegate, ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE))
    }
}

internal class AnalyticsTripTimelineVideoNotifier(
    private val delegate: TripTimelineVideoNotifier,
    private val telemetry: ShareMediaAnalytics,
) : TripTimelineVideoNotifier {
    override fun requestPermissionIfNeeded() = guard(ShareMediaOperation.NOTIFICATION_PERMISSION) {
        delegate.requestPermissionIfNeeded()
    }
    override fun showProgress(jobId: Long) = guard(ShareMediaOperation.NOTIFICATION_PROGRESS) { delegate.showProgress(jobId) }
    override fun clearProgress(jobId: Long) = guard(ShareMediaOperation.NOTIFICATION_CLEAR) { delegate.clearProgress(jobId) }
    override suspend fun showReady(jobId: Long): Boolean {
        val attempt = telemetry.start(ShareMediaOperation.NOTIFICATION_READY)
        return try {
            delegate.showReady(jobId).also { attempt.complete(if (it) "posted" else "not_posted") }
        } catch (error: Exception) {
            attempt.failed(error)
            throw error
        }
    }
    private inline fun guard(operation: ShareMediaOperation, block: () -> Unit) {
        try { block() } catch (error: Exception) {
            telemetry.recordObservedFailure(operation, error)
            throw error
        }
    }
}
