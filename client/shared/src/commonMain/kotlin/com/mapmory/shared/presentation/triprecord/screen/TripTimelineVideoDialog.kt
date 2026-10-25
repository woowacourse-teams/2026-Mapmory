package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.ShareMediaAnalytics
import com.mapmory.shared.analytics.ShareMediaSource
import com.mapmory.shared.analytics.ShareMediaAction
import com.mapmory.shared.analytics.ShareMediaOperation

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.remote.MapmoryConnectionException
import com.mapmory.shared.data.remote.toUserFriendlyRemoteFailure
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.repository.ShareVideoRepository
import com.mapmory.shared.presentation.triprecord.share.ShareVideoFile
import com.mapmory.shared.presentation.triprecord.share.TripTimelineVideoPreview
import com.mapmory.shared.presentation.triprecord.share.rememberAnalyticsTripTimelineVideoNotifier
import com.mapmory.shared.presentation.triprecord.share.rememberShareVideoExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock

@Composable
internal fun TripTimelineVideoDialog(
    repository: ShareVideoRepository,
    jobStore: ShareVideoJobStore,
    creationScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val analytics = LocalMapmoryAnalytics.current
    val telemetry = remember(analytics) { ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE) }
    LaunchedEffect(telemetry) { telemetry.action(ShareMediaAction.OPENED) }
    val exporter = rememberShareVideoExporter()
    val scope = rememberCoroutineScope()
    val notifier = rememberAnalyticsTripTimelineVideoNotifier()
    val jobState by jobStore.state.collectAsState()
    val jobId = jobState.jobId
    val status = jobState.status
    val creationInProgress = jobState.isCreationRequestInProgress
    var video by remember { mutableStateOf<ShareVideoFile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var previewAttemptedJobId by remember { mutableStateOf<Long?>(null) }
    val startedAtEpochMillis = jobState.startedAtEpochMillis
    val activeJob = jobId != null && status != ShareVideoStatus.COMPLETED && status != ShareVideoStatus.FAILED
    var nowEpochMillis by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }

    LaunchedEffect(startedAtEpochMillis, activeJob) {
        if (!activeJob || startedAtEpochMillis == null) return@LaunchedEffect
        while (true) {
            nowEpochMillis = Clock.System.now().toEpochMilliseconds()
            delay(1_000L)
        }
    }

    fun dismissDialog() {
        if (activeJob) notifier.showProgress(jobId)
        telemetry.action(ShareMediaAction.CLOSED)
        onDismiss()
    }

    fun createVideo() {
        if (busy) return
        if (!jobStore.tryStartCreationRequest()) {
            message = if (jobStore.state.value.isCreationRequestInProgress) {
                "영상 생성을 요청하고 있어요. 잠시만 기다려 주세요."
            } else {
                "이미 진행 중인 영상이 있어요. 현재 작업을 확인해 주세요."
            }
            return
        }
        telemetry.action(ShareMediaAction.CREATE_CLICKED)
        busy = true
        creationScope.launch {
            message = null
            video = null
            try {
                notifier.requestPermissionIfNeeded()
                val requestStartedAt = Clock.System.now().toEpochMilliseconds()
                val job = repository.createTravelTimeline().getOrThrow()
                jobStore.saveJobId(job.jobId, requestStartedAt)
                jobStore.updateStatus(job.status)
                if (job.status == ShareVideoStatus.FAILED) {
                    message = "영상 생성에 실패했어요. 다시 만들어 주세요."
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "영상 생성을 요청하지 못했어요. 다시 시도해 주세요."
            } finally {
                jobStore.finishCreationRequest()
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
                video = telemetry.observe(ShareMediaOperation.DOWNLOAD) {
                    exporter.download(download.downloadUrl, activeJobId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                when ((error as? MapmoryApiException)?.code) {
                    "SHARE_VIDEO_NOT_READY" -> {
                        jobStore.updateStatus(ShareVideoStatus.RENDERING)
                        message = "영상이 아직 준비 중이에요. 완료되면 미리보기를 불러올게요."
                    }
                    "SHARE_VIDEO_GENERATION_FAILED", "SHARE_VIDEO_JOB_NOT_FOUND" -> {
                        notifier.clearProgress(activeJobId)
                        jobStore.clear()
                        message = error.message ?: "영상 작업을 찾지 못했어요. 다시 만들어 주세요."
                    }
                    else -> {
                        val friendlyError = error.toUserFriendlyRemoteFailure()
                        message = if (friendlyError is MapmoryConnectionException) {
                            friendlyError.message
                        } else {
                            friendlyError.message ?: "영상을 다운로드하지 못했어요. 다시 시도해 주세요."
                        }
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    fun exportVideo(save: Boolean) {
        val downloadedVideo = video ?: return
        if (busy) return
        telemetry.action(if (save) ShareMediaAction.SAVE_CLICKED else ShareMediaAction.SHARE_CLICKED)
        scope.launch {
            busy = true
            message = null
            try {
                if (save) {
                    telemetry.observe(ShareMediaOperation.SAVE) { exporter.save(downloadedVideo) }
                    message = "동영상을 사진 앱에 저장했어요."
                } else {
                    telemetry.observe(ShareMediaOperation.SHARE_SHEET) { exporter.share(downloadedVideo) }
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

    LaunchedEffect(jobId, status, busy) {
        if (status != ShareVideoStatus.COMPLETED) {
            if (previewAttemptedJobId == jobId) previewAttemptedJobId = null
        } else if (jobId != null && !busy && video == null && previewAttemptedJobId != jobId) {
            previewAttemptedJobId = jobId
            downloadVideo()
        }
    }

    Dialog(
        onDismissRequest = { if (!creationInProgress) dismissDialog() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !creationInProgress,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.96f)
                .background(TripRecordPalette.current.surface, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 22.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("여행 타임라인 영상", color = TripRecordPalette.current.headingText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = ::dismissDialog, enabled = !creationInProgress) {
                    Text(if (creationInProgress) "요청 중…" else "닫기", color = TripRecordPalette.current.secondaryText)
                }
            }
            Spacer(Modifier.height(12.dp))
            val statusMessage = when (status) {
                ShareVideoStatus.PENDING -> "영상 생성 대기 중이에요."
                ShareVideoStatus.RENDERING -> "여행 기록을 영상으로 만들고 있어요."
                ShareVideoStatus.COMPLETED -> "여행 영상이 준비됐어요."
                ShareVideoStatus.FAILED -> "영상 생성에 실패했어요."
                null -> when {
                    creationInProgress -> "영상 생성을 요청하고 있어요."
                    jobId != null -> "저장된 영상 작업을 확인하고 있어요."
                    else -> "기록한 여행을 타임라인 영상으로 만들어 공유해요."
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(statusMessage, color = TripRecordPalette.current.secondaryText, fontSize = 14.sp)
                if (busy || activeJob) {
                    if (activeJob && startedAtEpochMillis != null) {
                        val elapsedSeconds = ((nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0L) / 1_000L)
                        Text(
                            "${elapsedSeconds / 60}:${(elapsedSeconds % 60).toString().padStart(2, '0')} 경과",
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 12.sp,
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = TripRecordPalette.current.primary,
                        trackColor = TripRecordPalette.current.primary.copy(alpha = 0.16f),
                    )
                }
            }
            message?.let {
                Text(it, color = TripRecordPalette.current.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
            jobState.statusCheckError?.let {
                Text(it, color = TripRecordPalette.current.secondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                OutlinedButton(
                    onClick = { telemetry.action(ShareMediaAction.STATUS_RETRY_CLICKED); jobStore.retryStatusCheck() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("지금 다시 확인") }
            }
            if (status == ShareVideoStatus.PENDING || status == ShareVideoStatus.RENDERING) {
                Text(
                    "화면을 닫아도 서버 생성은 계속돼요. 알림을 허용하면 알림 센터에 진행 중 상태가 남아요. 앱이 백그라운드에 있으면 완료 여부는 다시 열었을 때 확인돼요.",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            when {
                video != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("영상 미리보기", color = TripRecordPalette.current.headingText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    TripTimelineVideoPreview(
                        video = video!!,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { exportVideo(save = true) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("다운로드") }
                        Button(
                            onClick = { exportVideo(save = false) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TripRecordPalette.current.primary),
                        ) { Text("공유하기", color = Color.White) }
                    }
                }
                status == ShareVideoStatus.COMPLETED -> Column {
                    when {
                        busy -> Text("미리보기를 준비하고 있어요.", color = TripRecordPalette.current.secondaryText, fontSize = 13.sp)
                        message != null -> OutlinedButton(
                            onClick = { telemetry.action(ShareMediaAction.DOWNLOAD_RETRY_CLICKED); downloadVideo() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("미리보기 다시 불러오기") }
                    }
                    TextButton(
                        onClick = {
                            telemetry.action(ShareMediaAction.REGENERATE_CLICKED)
                            val completedJobId = jobId ?: return@TextButton
                            jobStore.clear()
                            notifier.clearProgress(completedJobId)
                            video = null
                            message = null
                            createVideo()
                        },
                        enabled = !busy && !creationInProgress,
                    ) { Text("최신 기록으로 다시 만들기", color = TripRecordPalette.current.primary) }
                }
                status == ShareVideoStatus.PENDING || status == ShareVideoStatus.RENDERING -> Unit
                jobId != null && status == null -> Unit
                else -> Button(
                    onClick = ::createVideo,
                    enabled = !busy && !creationInProgress,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TripRecordPalette.current.primary),
                ) {
                    Text(
                        when {
                            busy -> "요청 중…"
                            creationInProgress -> "요청 중…"
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
