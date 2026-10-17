package com.mapmory.android

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.MapmoryThemeState
import com.mapmory.shared.presentation.map.ui.KoreaMapArtwork
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KoreaMapFillAlignmentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun seongnamHasNoInternalFillGapInLightMode() = verifySeongnamFill(isDark = false)

    @Test
    fun seongnamHasNoInternalFillGapInDarkMode() = verifySeongnamFill(isDark = true)

    @Test
    fun gunwiRemainsVisibleOutsideTheOldDaeguSilhouette() {
        showMap(isDark = false, districtCode = "27720")
        val bounds = composeRule.onNodeWithTag("korea-map").fetchSemanticsNode().boundsInRoot
        val center = Offset(bounds.width / 2f, bounds.height / 2f)
        val point = project(bounds.width, bounds.height, 128.64807f, 36.17014f)
        val actual = capture(false, "initial-1.5", center + (point - center) * 1.5f, "gunwi")
        assertVisitedColor(false, "Gunwi initial-1.5", actual, tolerance = 4)
    }

    private fun showMap(isDark: Boolean, districtCode: String) {
        composeRule.setContent {
            CompositionLocalProvider(LocalMapmoryTheme provides MapmoryThemeState(isDark) {}) {
                Box(Modifier.fillMaxSize()) {
                    KoreaMapArtwork(
                        visitedRegionCodes = setOf(districtCode),
                        isProvinceOverview = true,
                        modifier = Modifier.testTag("korea-map"),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val map = composeRule.onNodeWithTag("korea-map")
        val visitedColor = androidx.compose.ui.graphics.Color(if (isDark) 0xFF35C987 else 0xFF4D9272).toArgb()
        composeRule.waitUntil(15_000) {
            val bitmap = map.captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            pixels.any { it == visitedColor }
        }
    }

    private fun verifySeongnamFill(isDark: Boolean) {
        showMap(isDark, "41130")
        val map = composeRule.onNodeWithTag("korea-map")
        val bounds = map.fetchSemanticsNode().boundsInRoot
        val center = Offset(bounds.width / 2f, bounds.height / 2f)
        val seamPoint = project(bounds.width, bounds.height, 127.121f, 37.413f)
        val initialPixel = center + (seamPoint - center) * 1.5f
        val initialColor = capture(isDark, "initial-1.5", initialPixel)

        // Two pinches reach the limit even when device touch slop consumes the first moves.
        repeat(2) {
            map.performTouchInput {
                down(0, center - Offset(width * 0.05f, 0f))
                down(1, center + Offset(width * 0.05f, 0f))
            }
            repeat(20) { frame ->
                val distance = bounds.width * (0.05f + 0.35f * (frame + 1) / 20f)
                map.performTouchInput {
                    updatePointerTo(0, center - Offset(distance, 0f))
                    updatePointerTo(1, center + Offset(distance, 0f))
                    move()
                }
                composeRule.waitForIdle()
            }
            map.performTouchInput { up(0); up(1) }
            composeRule.waitForIdle()
        }
        val pan = (center - seamPoint) * 12f
        val steps = ceil(maxOf(abs(pan.x) / (bounds.width * 0.2f), abs(pan.y) / (bounds.height * 0.2f)))
            .toInt().coerceAtLeast(1)
        repeat(steps) {
            map.performTouchInput { down(center) }
            map.performTouchInput { moveBy(pan / steps.toFloat()) }
            composeRule.waitForIdle()
            map.performTouchInput { up() }
            composeRule.waitForIdle()
        }
        val maximumPixel = locateSeongnamSeamAtMaximum(isDark, bounds.width, bounds.height, seamPoint)
        val maximumColor = capture(isDark, "maximum-12", maximumPixel)
        assertVisitedColor(isDark, "initial-1.5", initialColor, tolerance = 40)
        assertVisitedColor(isDark, "maximum-12", maximumColor, tolerance = 4)
    }

    private fun locateSeongnamSeamAtMaximum(isDark: Boolean, width: Float, height: Float, seam: Offset): Offset {
        val bitmap = composeRule.onNodeWithTag("korea-map").captureToImage().asAndroidBitmap()
        val expected = androidx.compose.ui.graphics.Color(if (isDark) 0xFF35C987 else 0xFF4D9272).toArgb()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var right = 0
        var bottom = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (pixels[y * bitmap.width + x] == expected) {
                    left = minOf(left, x)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        // Exterior extrema stay fixed across the seam fix, calibrating gesture slop without hiding gaps.
        val west = project(width, height, 127.02792f, 37.33493f)
        val east = project(width, height, 127.19543f, 37.33493f)
        assertTrue("Pinch must reach the 12x limit: actual width=${right - left}, expected=${(east.x - west.x) * 12f}", abs((right - left) - (east.x - west.x) * 12f) <= 5f)
        return Offset(left + (seam.x - west.x) * 12f, bottom + (seam.y - west.y) * 12f)
    }

    private fun capture(isDark: Boolean, scale: String, pixel: Offset, fixture: String = "seongnam"): Int {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag("korea-map").captureToImage().asAndroidBitmap()
        val mode = if (isDark) "dark" else "light"
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "map-fill-evidence",
        ).apply { mkdirs() }
        File(directory, "$fixture-$mode-$scale.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        return bitmap.getPixel(pixel.x.roundToInt(), pixel.y.roundToInt())
    }

    private fun assertVisitedColor(isDark: Boolean, scale: String, actual: Int, tolerance: Int) {
        val mode = if (isDark) "dark" else "light"
        val expected = androidx.compose.ui.graphics.Color(if (isDark) 0xFF35C987 else 0xFF4D9272).toArgb()
        val difference = listOf(16, 8, 0).maxOf { shift ->
            abs(((actual shr shift) and 0xFF) - ((expected shr shift) and 0xFF))
        }
        assertTrue(
            "$mode $scale: Seongnam interior must be green, actual=${actual.toUInt().toString(16)}",
            difference <= tolerance,
        )
    }

    private fun project(width: Float, height: Float, longitude: Float, latitude: Float): Offset {
        // Fixture bounds mirror the existing national map; production keeps its private projection.
        val minLongitude = 124.60972f
        val maxLongitude = 131.87147f
        val minLatitude = 33.11236f
        val maxLatitude = 38.61371f
        val longitudeFactor = cos((minLatitude + maxLatitude) / 2f * Math.PI.toFloat() / 180f)
        val longitudeSpan = (maxLongitude - minLongitude) * longitudeFactor
        val latitudeSpan = maxLatitude - minLatitude
        val scale = min(width * 0.91f / longitudeSpan, height * 0.88f / latitudeSpan)
        val left = (width - longitudeSpan * scale) / 2f
        val top = ((height - latitudeSpan * scale) / 2f - height * 0.07f).coerceAtLeast(height * 0.06f)
        return Offset(
            left + (longitude - minLongitude) * longitudeFactor * scale,
            top + (maxLatitude - latitude) * scale,
        )
    }
}
