package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable

internal interface TripTimelineVideoNotifier {
    fun requestPermissionIfNeeded()

    fun showReady(jobId: Long)
}

@Composable
internal expect fun rememberTripTimelineVideoNotifier(): TripTimelineVideoNotifier
