package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberShareVideoExporter(): ShareVideoExporter = remember {
    object : ShareVideoExporter {
        override suspend fun download(url: String): ShareVideoFile =
            error("영상 다운로드는 Android와 iOS 앱에서 사용할 수 있어요.")

        override suspend fun save(video: ShareVideoFile) {
            error("영상 저장은 Android와 iOS 앱에서 사용할 수 있어요.")
        }

        override suspend fun share(video: ShareVideoFile) {
            error("외부 공유는 Android와 iOS 앱에서 사용할 수 있어요.")
        }
    }
}
