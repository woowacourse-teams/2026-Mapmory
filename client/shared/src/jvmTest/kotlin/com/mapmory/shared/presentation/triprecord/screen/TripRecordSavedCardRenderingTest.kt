package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.MapmoryThemeState
import com.mapmory.shared.app.BackgroundTripRecordCompletion
import com.mapmory.shared.data.settings.MapmoryThemePalette
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class TripRecordSavedCardRenderingTest {
    @Test
    fun `saved card renders with contrast in light and dark themes`() {
        for (dark in listOf(false, true)) {
            val scene = ImageComposeScene(width = 390, height = 180) {
                CompositionLocalProvider(LocalMapmoryTheme provides MapmoryThemeState(isDark = dark, palette = MapmoryThemePalette.MAPMORY_GREEN, onThemeChange = {}, onPaletteChange = {})) {
                    ProvideTripRecordPalettes(isDark = dark) {
                    TripRecordTheme {
                        Box(Modifier.fillMaxSize().background(TripRecordPalette.current.pageBackground).padding(20.dp), contentAlignment = Alignment.Center) {
                            TripRecordSavedCard(
                                completion = BackgroundTripRecordCompletion(recordId = 42, previousStatistics = null, locationName = "제주", photoCount = 8),
                                onView = {},
                            )
                        }
                    }
                    }
                }
            }
            try {
                repeat(3) { scene.render(it * 16_666_667L).close() }
                scene.render(64_000_000L).use { rendered ->
                    val pixels = rendered.toComposeImageBitmap().toPixelMap()
                    assertTrue(if (dark) pixels[0, 0].red < 0.2f else pixels[0, 0].red > 0.9f)
                    val colors = mutableSetOf<Int>()
                    for (y in 30 until 150) for (x in 80 until 370) colors += pixels[x, y].hashCode()
                    assertTrue(colors.size > 20, "Card text and action must render distinctly in both themes")
                    val output = File("build/reports/saved-card-${if (dark) "dark" else "light"}.png")
                    output.parentFile.mkdirs()
                    rendered.encodeToData()!!.use { output.writeBytes(it.bytes) }
                }
            } finally {
                scene.close()
            }
        }
    }
}
