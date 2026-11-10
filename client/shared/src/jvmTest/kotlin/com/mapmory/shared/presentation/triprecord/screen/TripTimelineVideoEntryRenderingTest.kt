package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.MapmoryThemeState
import com.mapmory.shared.data.settings.MapmoryThemePalette
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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
                        LocalMapmoryTheme provides MapmoryThemeState(
                            isDark = dark,
                            palette = MapmoryThemePalette.MAPMORY_GREEN,
                            onThemeChange = {},
                            onPaletteChange = {},
                        ),
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

    @Test
    fun `국가_수를_불러오는_동안에도_불러온_뒤와_같은_높이를_유지한다`() {
        val loadingHeight = renderExpandedHeight(countryCount = null, isCountryCountLoading = true)
        val loadedHeight = renderExpandedHeight(countryCount = 0, isCountryCountLoading = false)
        assertEquals(loadedHeight, loadingHeight, "국가 수 응답 뒤 카드 높이가 바뀌면 아래 목록이 밀린다")
    }

    private fun renderExpandedHeight(countryCount: Int?, isCountryCountLoading: Boolean): Int {
        var height = 0
        val scene = ImageComposeScene(width = 354, height = 400) {
            CompositionLocalProvider(
                LocalMapmoryTheme provides MapmoryThemeState(
                    isDark = false,
                    palette = MapmoryThemePalette.MAPMORY_GREEN,
                    onThemeChange = {},
                    onPaletteChange = {},
                ),
            ) {
                ProvideTripRecordPalettes(isDark = false) {
                    TripRecordTheme {
                        TripTimelineVideoEntry(
                            countryCount = countryCount,
                            isCountryCountLoading = isCountryCountLoading,
                            onClick = {},
                            modifier = Modifier.onSizeChanged { height = it.height },
                        )
                    }
                }
            }
        }
        try {
            repeat(3) { scene.render(it * 16_666_667L).close() }
        } finally {
            scene.close()
        }
        return height
    }
}
