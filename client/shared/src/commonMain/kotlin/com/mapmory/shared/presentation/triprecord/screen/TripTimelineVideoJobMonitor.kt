package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.repository.ShareVideoRepository
import com.mapmory.shared.presentation.triprecord.share.rememberTripTimelineVideoNotifier
import kotlinx.coroutines.delay

@Composable
internal fun TripTimelineVideoJobMonitor(
    repository: ShareVideoRepository,
    jobStore: ShareVideoJobStore,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val notifier = rememberTripTimelineVideoNotifier()
    val jobState by jobStore.state.collectAsState()
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(repository, jobStore, foreground, jobState.jobId, jobState.status) {
        val jobId = jobState.jobId ?: return@LaunchedEffect
        if (!foreground || jobState.status == ShareVideoStatus.FAILED ||
            (jobState.status == ShareVideoStatus.COMPLETED && jobStore.hasCompletionBeenNotified(jobId))
        ) return@LaunchedEffect

        var retryDelay = 2_000L
        while (true) {
            val result = repository.getJob(jobId)
            if (result.isSuccess) {
                val status = result.getOrThrow().status
                when (status) {
                    ShareVideoStatus.COMPLETED -> {
                        if (!jobStore.hasCompletionBeenNotified(jobId)) {
                            jobStore.markCompletionNotified(jobId)
                            notifier.showReady(jobId)
                        }
                        jobStore.updateStatus(status)
                        break
                    }
                    ShareVideoStatus.FAILED -> {
                        jobStore.updateStatus(status)
                        break
                    }
                    ShareVideoStatus.PENDING, ShareVideoStatus.RENDERING -> {
                        jobStore.updateStatus(status)
                        retryDelay = 2_000L
                        delay(retryDelay)
                    }
                }
            } else {
                val error = result.exceptionOrNull() ?: IllegalStateException()
                if (error is MapmoryApiException && error.code == "SHARE_VIDEO_JOB_NOT_FOUND") {
                    jobStore.clear()
                    break
                }
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(15_000L)
            }
        }
    }
}
