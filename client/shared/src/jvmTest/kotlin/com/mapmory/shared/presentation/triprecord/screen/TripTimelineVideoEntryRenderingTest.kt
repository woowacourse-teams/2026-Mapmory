package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.MapmoryThemeState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class TripTimelineVideoEntryRenderingTest {
    @Test
    fun `미리보기_확인_후에는_진입_버튼이_접혀_사진_공간을_확보한다`() {
        for (dark in listOf(false, true)) {
            val heights = mutableListOf<Int>()
            for (compact in listOf(false, true)) {
                var height = 0
                val scene = ImageComposeScene(width = 354, height = 320) {
                    CompositionLocalProvider(
                        LocalMapmoryTheme provides MapmoryThemeState(isDark = dark, onThemeChange = {}),
                    ) {
                        ProvideTripRecordPalettes(isDark = dark) {
                            TripRecordTheme {
                                TripTimelineVideoEntry(
                                    countryCount = 2,
                                    compact = compact,
                                    onClick = {},
                                    modifier = Modifier.onSizeChanged { height = it.height },
                                )
                            }
                        }
                    }
                }
                try {
                    repeat(3) { scene.render(it * 16_666_667L).close() }
                    scene.render(64_000_000L).use { rendered ->
                        val output = File("build/reports/video-entry-${if (compact) "compact" else "expanded"}-${if (dark) "dark" else "light"}.png")
                        output.parentFile.mkdirs()
                        rendered.encodeToData()!!.use { output.writeBytes(it.bytes) }
                    }
                    heights += height
                } finally {
                    scene.close()
                }
            }
            assertTrue(heights[1] >= 48, "접힌 버튼도 최소 터치 영역을 유지한다")
            assertTrue(heights[1] < heights[0] / 2, "접힌 버튼이 사진 목록에 공간을 돌려준다")
        }
    }
}
