package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

internal interface ShareImageExporter {
    suspend fun save(image: ImageBitmap)
    suspend fun share(image: ImageBitmap)
}

@Composable
internal expect fun rememberShareImageExporter(): ShareImageExporter
