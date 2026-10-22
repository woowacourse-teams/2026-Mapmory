package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable

internal data class ShareVideoFile(val path: String)

internal interface ShareVideoExporter {
    suspend fun download(url: String): ShareVideoFile
    suspend fun save(video: ShareVideoFile)
    suspend fun share(video: ShareVideoFile)
}

@Composable
internal expect fun rememberShareVideoExporter(): ShareVideoExporter
