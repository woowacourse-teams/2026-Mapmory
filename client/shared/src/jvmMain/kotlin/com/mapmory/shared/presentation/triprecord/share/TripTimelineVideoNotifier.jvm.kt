package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberTripTimelineVideoNotifier(): TripTimelineVideoNotifier =
    remember { JvmTripTimelineVideoNotifier() }

private class JvmTripTimelineVideoNotifier : TripTimelineVideoNotifier {
    override fun requestPermissionIfNeeded() = Unit

    override fun showReady(jobId: Long) = Unit
}
