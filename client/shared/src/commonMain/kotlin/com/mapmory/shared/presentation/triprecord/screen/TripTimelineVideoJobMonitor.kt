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
import com.mapmory.shared.data.remote.MapmoryConnectionException
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.remote.toUserFriendlyRemoteFailure
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.repository.ShareVideoRepository
import com.mapmory.shared.presentation.triprecord.share.rememberAnalyticsTripTimelineVideoNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@Composable
internal fun TripTimelineVideoJobMonitor(
    repository: ShareVideoRepository,
    jobStore: ShareVideoJobStore,
    notificationEntry: Boolean = false,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val notifier = rememberAnalyticsTripTimelineVideoNotifier()
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

    LaunchedEffect(repository, jobStore, foreground, jobState.jobId, jobState.status, jobState.statusCheckRetryCount) {
        val jobId = jobState.jobId ?: return@LaunchedEffect
        if (jobState.status == ShareVideoStatus.FAILED) {
            notifier.clearProgress(jobId)
            return@LaunchedEffect
        }
        if (jobState.status == ShareVideoStatus.COMPLETED) {
            notifier.clearProgress(jobId)
            if (jobState.serverPushRequested) {
                jobStore.markCompletionNotified(jobId)
            } else if (!notificationEntry && !jobStore.hasCompletionBeenNotified(jobId)) {
                if (notifier.showReady(jobId)) jobStore.markCompletionNotified(jobId)
            }
            return@LaunchedEffect
        }
        if (!foreground) return@LaunchedEffect

        var retryDelay = 2_000L
        var consecutiveFailures = 0
        while (true) {
            val result = try {
                repository.getJob(jobId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            currentCoroutineContext().ensureActive()
            if (jobStore.state.value.jobId != jobId) break
            if (result.isSuccess) {
                val status = result.getOrThrow().status
                when (status) {
                    ShareVideoStatus.COMPLETED -> {
                        notifier.clearProgress(jobId)
                        if (jobState.serverPushRequested) {
                            jobStore.markCompletionNotified(jobId)
                        } else if (!notificationEntry && !jobStore.hasCompletionBeenNotified(jobId)) {
                            if (notifier.showReady(jobId)) jobStore.markCompletionNotified(jobId)
                        }
                        jobStore.updateStatus(status)
                        break
                    }
                    ShareVideoStatus.FAILED -> {
                        notifier.clearProgress(jobId)
                        jobStore.updateStatus(status)
                        break
                    }
                    ShareVideoStatus.PENDING, ShareVideoStatus.RENDERING -> {
                        jobStore.updateStatus(status)
                        consecutiveFailures = 0
                        retryDelay = 2_000L
                        delay(retryDelay)
                    }
                }
            } else {
                val error = result.exceptionOrNull() ?: IllegalStateException()
                if (error is MapmoryApiException && error.code == "SHARE_VIDEO_JOB_NOT_FOUND") {
                    notifier.clearProgress(jobId)
                    if (notificationEntry) {
                        jobStore.updateStatusCheckError("이 영상을 확인할 수 없어요. 로그인 계정과 연결 상태를 확인한 뒤 다시 시도해 주세요.")
                    } else {
                        jobStore.clear()
                    }
                    break
                }
                val friendlyError = error.toUserFriendlyRemoteFailure()
                consecutiveFailures += 1
                if (consecutiveFailures >= 5) {
                    jobStore.updateStatusCheckError(
                        if (notificationEntry) {
                            "영상 상태를 확인하지 못했어요. 연결 상태를 확인한 뒤 다시 시도해 주세요."
                        } else if (friendlyError is MapmoryConnectionException) {
                            "네트워크 연결이 불안정해 영상 상태를 확인하지 못했어요. 같은 작업으로 다시 확인할 수 있어요."
                        } else {
                            "${friendlyError.message ?: "영상 상태를 확인하지 못했어요."} 같은 작업으로 다시 확인할 수 있어요."
                        },
                    )
                    break
                }
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(15_000L)
            }
        }
    }
}
