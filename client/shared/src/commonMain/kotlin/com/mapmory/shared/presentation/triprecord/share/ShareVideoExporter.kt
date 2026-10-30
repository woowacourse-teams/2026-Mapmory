package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette

internal data class ShareVideoFile(val path: String)

internal interface ShareVideoExporter {
    suspend fun download(url: String, jobId: Long, protectedJobId: Long? = null): ShareVideoFile
    suspend fun save(video: ShareVideoFile)
    suspend fun share(video: ShareVideoFile)
}

@Composable
internal expect fun rememberShareVideoExporter(): ShareVideoExporter

@Composable
internal fun TripTimelineVideoPreview(video: ShareVideoFile, modifier: Modifier = Modifier) {
    val controller = remember(video.path) { TripTimelineVideoPlaybackController() }
    var isSeeking by remember(video.path) { mutableStateOf(false) }
    var sliderPosition by remember(video.path) { mutableLongStateOf(0L) }
    val duration = controller.durationMillis
    val position = if (isSeeking) sliderPosition else controller.positionMillis
    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        TripTimelineVideoSurface(video, controller, Modifier.fillMaxSize())
        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackButton(
                isPlaying = controller.isPlaying,
                enabled = controller.isReady,
                onClick = controller::togglePlayback,
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = progress,
                onValueChange = {
                    isSeeking = true
                    sliderPosition = (it * duration).toLong()
                },
                onValueChangeFinished = {
                    controller.seekTo(sliderPosition)
                    isSeeking = false
                },
                enabled = controller.isReady && duration > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = TripRecordPalette.current.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.38f),
                    disabledThumbColor = Color.White.copy(alpha = 0.55f),
                    disabledActiveTrackColor = TripRecordPalette.current.primary.copy(alpha = 0.55f),
                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.24f),
                ),
            )
            Text(
                "${formatVideoTime(position)} / ${formatVideoTime(duration)}",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.disconnect() }
    }
}

internal class TripTimelineVideoPlaybackController {
    var isPlaying by mutableStateOf(false)
        private set
    var isReady by mutableStateOf(false)
        private set
    var positionMillis by mutableLongStateOf(0L)
        private set
    var durationMillis by mutableLongStateOf(0L)
        private set

    private var toggleAction: (() -> Unit)? = null
    private var seekAction: ((Long) -> Unit)? = null

    fun togglePlayback() {
        if (isReady) toggleAction?.invoke()
    }

    fun seekTo(positionMillis: Long) {
        val position = positionMillis.coerceIn(0L, durationMillis)
        this.positionMillis = position
        seekAction?.invoke(position)
    }

    internal fun connect(toggle: () -> Unit, seek: (Long) -> Unit) {
        toggleAction = toggle
        seekAction = seek
    }

    internal fun update(isPlaying: Boolean, positionMillis: Long, durationMillis: Long) {
        this.isPlaying = isPlaying
        this.durationMillis = durationMillis.coerceAtLeast(0L)
        this.positionMillis = positionMillis.coerceIn(0L, this.durationMillis)
        isReady = this.durationMillis > 0L
    }

    internal fun disconnect() {
        toggleAction = null
        seekAction = null
    }
}

@Composable
private fun PlaybackButton(isPlaying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.1f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "일시정지" else "재생" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(15.dp)) {
            if (isPlaying) {
                val strokeWidth = size.width * 0.27f
                drawLine(
                    Color.White,
                    Offset(size.width * 0.30f, size.height * 0.18f),
                    Offset(size.width * 0.30f, size.height * 0.82f),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    Color.White,
                    Offset(size.width * 0.70f, size.height * 0.18f),
                    Offset(size.width * 0.70f, size.height * 0.82f),
                    strokeWidth,
                    cap = StrokeCap.Round,
                )
            } else {
                val path = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.12f)
                    lineTo(size.width * 0.84f, size.height * 0.5f)
                    lineTo(size.width * 0.24f, size.height * 0.88f)
                    close()
                }
                drawPath(path, Color.White)
            }
        }
    }
}

private fun formatVideoTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1_000L).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
internal expect fun TripTimelineVideoSurface(
    video: ShareVideoFile,
    controller: TripTimelineVideoPlaybackController,
    modifier: Modifier = Modifier,
)
