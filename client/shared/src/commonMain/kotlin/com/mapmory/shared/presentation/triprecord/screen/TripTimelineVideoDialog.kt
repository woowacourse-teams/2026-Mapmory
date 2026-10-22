package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.repository.ShareVideoRepository
import com.mapmory.shared.presentation.triprecord.share.ShareVideoFile
import com.mapmory.shared.presentation.triprecord.share.rememberShareVideoExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun TripTimelineVideoDialog(
    repository: ShareVideoRepository,
    jobStore: ShareVideoJobStore,
    onDismiss: () -> Unit,
) {
    val exporter = rememberShareVideoExporter()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var jobId by remember(jobStore) { mutableStateOf(jobStore.loadJobId()) }
    var status by remember(jobStore) { mutableStateOf<ShareVideoStatus?>(null) }
    var video by remember { mutableStateOf<ShareVideoFile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pollTrigger by remember { mutableStateOf(0) }

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

    LaunchedEffect(jobId, foreground, pollTrigger) {
        val activeJobId = jobId ?: return@LaunchedEffect
        if (!foreground) return@LaunchedEffect
        var retryDelay = 2_000L
        while (true) {
            val result = repository.getJob(activeJobId)
            if (result.isSuccess) {
                val job = result.getOrThrow()
                status = job.status
                message = null
                when (job.status) {
                    ShareVideoStatus.COMPLETED -> {
                        busy = true
                        try {
                            val download = repository.getDownloadUrl(activeJobId).getOrThrow()
                            video = exporter.download(download.downloadUrl)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            when ((error as? MapmoryApiException)?.code) {
                                "SHARE_VIDEO_NOT_READY" -> {
                                    status = ShareVideoStatus.RENDERING
                                    message = "영상이 아직 준비 중이에요. 완료되면 다시 다운로드해 주세요."
                                    delay(2_000L)
                                    continue
                                }
                                "SHARE_VIDEO_GENERATION_FAILED", "SHARE_VIDEO_JOB_NOT_FOUND" -> {
                                    jobStore.clear()
                                    jobId = null
                                    status = ShareVideoStatus.FAILED
                                    message = error.message ?: "영상 작업을 찾지 못했어요. 다시 만들어 주세요."
                                }
                                else -> message = error.message ?: "영상을 다운로드하지 못했어요. 다시 시도해 주세요."
                            }
                        } finally {
                            busy = false
                        }
                        break
                    }
                    ShareVideoStatus.FAILED -> {
                        jobStore.clear()
                        jobId = null
                        message = "영상 생성에 실패했어요. 다시 만들어 주세요."
                        break
                    }
                    ShareVideoStatus.PENDING, ShareVideoStatus.RENDERING -> {
                        retryDelay = 2_000L
                        delay(retryDelay)
                    }
                }
            } else {
                val error = result.exceptionOrNull() ?: IllegalStateException()
                if (error is MapmoryApiException && error.code == "SHARE_VIDEO_JOB_NOT_FOUND") {
                    jobStore.clear()
                    jobId = null
                    status = null
                    message = "저장된 영상 작업을 찾지 못했어요. 다시 만들어 주세요."
                    break
                }
                message = error.message ?: "영상 상태를 확인하지 못했어요. 다시 시도하고 있어요."
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(15_000L)
            }
        }
    }

    fun createVideo() {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            video = null
            try {
                val job = repository.createTravelTimeline().getOrThrow()
                if (job.status == ShareVideoStatus.FAILED) {
                    jobStore.clear()
                    jobId = null
                    status = ShareVideoStatus.FAILED
                    message = "영상 생성에 실패했어요. 다시 만들어 주세요."
                } else {
                    jobStore.saveJobId(job.jobId)
                    jobId = job.jobId
                    status = job.status
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "영상 생성을 요청하지 못했어요. 다시 시도해 주세요."
            } finally {
                busy = false
            }
        }
    }

    fun downloadVideo() {
        val activeJobId = jobId ?: return
        if (busy) return
        scope.launch {
            busy = true
            message = null
            video = null
            try {
                val download = repository.getDownloadUrl(activeJobId).getOrThrow()
                video = exporter.download(download.downloadUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                when ((error as? MapmoryApiException)?.code) {
                    "SHARE_VIDEO_NOT_READY" -> {
                        status = ShareVideoStatus.RENDERING
                        message = "영상이 아직 준비 중이에요. 완료되면 다시 다운로드해 주세요."
                        pollTrigger += 1
                    }
                    "SHARE_VIDEO_GENERATION_FAILED", "SHARE_VIDEO_JOB_NOT_FOUND" -> {
                        jobStore.clear()
                        jobId = null
                        status = ShareVideoStatus.FAILED
                        message = error.message ?: "영상 작업을 찾지 못했어요. 다시 만들어 주세요."
                    }
                    else -> message = error.message ?: "영상을 다운로드하지 못했어요. 다시 시도해 주세요."
                }
            } finally {
                busy = false
            }
        }
    }

    fun exportVideo(save: Boolean) {
        val downloadedVideo = video ?: return
        if (busy) return
        scope.launch {
            busy = true
            message = null
            try {
                if (save) {
                    exporter.save(downloadedVideo)
                    message = "동영상을 사진 앱에 저장했어요."
                } else {
                    exporter.share(downloadedVideo)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "동영상을 내보내지 못했어요. 다시 시도해 주세요."
            } finally {
                busy = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.9f)
                .background(TripRecordPalette.current.surface, RoundedCornerShape(28.dp))
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("여행 타임라인 영상", color = TripRecordPalette.current.headingText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("닫기", color = TripRecordPalette.current.secondaryText) }
            }
            Spacer(Modifier.height(12.dp))
            val statusMessage = when (status) {
                ShareVideoStatus.PENDING -> "영상 생성 대기 중이에요."
                ShareVideoStatus.RENDERING -> "여행 기록을 영상으로 만들고 있어요."
                ShareVideoStatus.COMPLETED -> "여행 영상이 준비됐어요."
                ShareVideoStatus.FAILED -> "영상 생성에 실패했어요."
                null -> if (jobId != null) "저장된 영상 작업을 확인하고 있어요." else "기록한 여행을 타임라인 영상으로 만들어 공유해요."
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy || (jobId != null && status != ShareVideoStatus.COMPLETED && status != ShareVideoStatus.FAILED)) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp),
                        color = TripRecordPalette.current.primary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(statusMessage, color = TripRecordPalette.current.secondaryText, fontSize = 14.sp)
            }
            message?.let {
                Text(it, color = TripRecordPalette.current.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(22.dp))
            when {
                video != null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { exportVideo(save = true) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("동영상 저장") }
                    Button(
                        onClick = { exportVideo(save = false) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TripRecordPalette.current.primary),
                    ) { Text("공유하기", color = Color.White) }
                }
                status == ShareVideoStatus.COMPLETED -> Button(
                    onClick = ::downloadVideo,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TripRecordPalette.current.primary),
                ) { Text(if (busy) "다운로드 중…" else "영상 다운로드", color = Color.White, fontWeight = FontWeight.SemiBold) }
                status == ShareVideoStatus.PENDING || status == ShareVideoStatus.RENDERING -> Unit
                jobId != null && status == null -> Unit
                else -> Button(
                    onClick = ::createVideo,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TripRecordPalette.current.primary),
                ) {
                    Text(
                        when {
                            busy -> "요청 중…"
                            status == ShareVideoStatus.FAILED -> "다시 만들기"
                            jobId == null -> "영상 만들기"
                            else -> "다시 시도"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
