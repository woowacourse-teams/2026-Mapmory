package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
internal actual fun TripTimelineVideoSurface(
    video: ShareVideoFile,
    controller: TripTimelineVideoPlaybackController,
    modifier: Modifier,
) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Text("영상 미리보기는 Android와 iOS 앱에서 확인해 주세요.", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
internal actual fun rememberShareVideoExporter(): ShareVideoExporter = remember {
    object : ShareVideoExporter {
        override suspend fun download(url: String, jobId: Long, protectedJobId: Long?): ShareVideoFile =
            error("영상 다운로드는 Android와 iOS 앱에서 사용할 수 있어요.")

        override suspend fun save(video: ShareVideoFile) {
            error("영상 저장은 Android와 iOS 앱에서 사용할 수 있어요.")
        }

        override suspend fun share(video: ShareVideoFile) {
            error("외부 공유는 Android와 iOS 앱에서 사용할 수 있어요.")
        }
    }
}
