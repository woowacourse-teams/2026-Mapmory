package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable

internal interface TripTimelineVideoNotifier {
    suspend fun requestPermissionIfNeeded()

    fun showProgress(jobId: Long)

    fun clearProgress(jobId: Long)

    suspend fun showReady(jobId: Long): Boolean
}

@Composable
internal expect fun rememberTripTimelineVideoNotifier(): TripTimelineVideoNotifier
