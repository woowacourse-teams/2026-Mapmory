package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

@Composable
internal actual fun rememberShareImageExporter(): ShareImageExporter = remember {
    object : ShareImageExporter {
        override suspend fun save(image: ImageBitmap) {
            error("이미지 저장은 Android와 iOS 앱에서 사용할 수 있어요.")
        }
        override suspend fun share(image: ImageBitmap) {
            error("외부 공유는 Android와 iOS 앱에서 사용할 수 있어요.")
        }
    }
}
