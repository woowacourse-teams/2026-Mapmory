package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal data class ShareVideoFile(val path: String)

internal interface ShareVideoExporter {
    suspend fun download(url: String, jobId: Long): ShareVideoFile
    suspend fun save(video: ShareVideoFile)
    suspend fun share(video: ShareVideoFile)
}

@Composable
internal expect fun rememberShareVideoExporter(): ShareVideoExporter

@Composable
internal expect fun TripTimelineVideoPreview(
    video: ShareVideoFile,
    modifier: Modifier = Modifier,
)
