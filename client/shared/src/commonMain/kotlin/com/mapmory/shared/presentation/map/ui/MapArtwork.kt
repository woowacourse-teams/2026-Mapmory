package com.mapmory.shared.presentation.map.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.domain.ProvincePolygon
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewVisitedCountries

@Composable
internal fun rememberMapSearchFocusPulse(focusedRegionCode: String?): Animatable<Float, AnimationVector1D> {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(focusedRegionCode) {
        pulse.snapTo(0f)
        if (focusedRegionCode != null) {
            pulse.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = SearchFocusPulseHalfCycleMillis, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }
    }
    return pulse
}

internal fun mapSearchFocusOutlineAlpha(pulse: Float): Float =
    MinSearchFocusOutlineAlpha + pulse.coerceIn(0f, 1f) * (MaxSearchFocusOutlineAlpha - MinSearchFocusOutlineAlpha)

internal fun mapSearchFocusGlowAlpha(pulse: Float): Float =
    MinSearchFocusGlowAlpha + pulse.coerceIn(0f, 1f) * (MaxSearchFocusGlowAlpha - MinSearchFocusGlowAlpha)

internal fun mapSearchFocusWhiteHighlightAlpha(pulse: Float): Float =
    pulse.coerceIn(0f, 1f) * MaxSearchFocusWhiteHighlightAlpha

internal fun DrawScope.drawMapSearchFocusPulse(
    path: Path,
    pulse: Float,
    color: Color,
    outlineWidth: Float,
    canvasScale: Float = 1f,
) {
    drawPath(
        path = path,
        color = color.copy(alpha = mapSearchFocusGlowAlpha(pulse)),
        style = Stroke(width = SearchFocusPulseGlowWidth.toPx() / canvasScale),
    )
    val whiteHighlightAlpha = mapSearchFocusWhiteHighlightAlpha(pulse)
    drawPath(
        path = path,
        color = Color.White.copy(alpha = whiteHighlightAlpha * WhiteHighlightGlowRatio),
        style = Stroke(width = SearchFocusPulseGlowWidth.toPx() / canvasScale),
    )
    drawPath(
        path = path,
        color = color.copy(alpha = mapSearchFocusOutlineAlpha(pulse)),
        style = Stroke(width = outlineWidth),
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = whiteHighlightAlpha),
        style = Stroke(width = outlineWidth),
    )
}

private const val MinSearchFocusOutlineAlpha = 0.60f
private const val MaxSearchFocusOutlineAlpha = 0.92f
private const val MinSearchFocusGlowAlpha = 0.04f
private const val MaxSearchFocusGlowAlpha = 0.12f
private const val MaxSearchFocusWhiteHighlightAlpha = 0.78f
private const val WhiteHighlightGlowRatio = 0.18f
private const val SearchFocusPulseHalfCycleMillis = 900
private val SearchFocusPulseGlowWidth = 7.dp

@Composable
fun MapArtwork(
    scope: MapScope = MapScope.WORLD,
    visitedCountryCodes: Set<String> = emptySet(),
    visitedRegionCodes: Set<String> = emptySet(),
    koreaRegions: List<ProvincePolygon>? = null,
    showRegionLabels: Boolean = false,
    isProvinceOverview: Boolean = false,
    searchFocusCountryCode: String? = null,
    searchFocusRegionCode: String? = null,
    searchFocusRequest: Int = 0,
    currentLocation: GeoPoint? = null,
    currentLocationFocusRequest: Int = 0,
    onCountryClick: (String) -> Unit = {},
    onRegionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (scope) {
        MapScope.WORLD -> WorldGlobe(
            visitedCountryCodes = visitedCountryCodes,
            searchFocusCountryCode = searchFocusCountryCode,
            searchFocusRequest = searchFocusRequest,
            currentLocation = currentLocation,
            currentLocationFocusRequest = currentLocationFocusRequest,
            onCountryClick = onCountryClick,
            modifier = modifier,
        )

        MapScope.KOREA -> KoreaMapArtwork(
            regions = koreaRegions ?: com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData.provinces,
            visitedRegionCodes = visitedRegionCodes,
            showRegionLabels = showRegionLabels,
            isProvinceOverview = isProvinceOverview,
            searchFocusRegionCode = searchFocusRegionCode,
            searchFocusRequest = searchFocusRequest,
            currentLocation = currentLocation,
            currentLocationFocusRequest = currentLocationFocusRequest,
            onRegionClick = onRegionClick,
            modifier = modifier,
        )
    }
}

@Preview(
    name = "세계 지도 아트워크",
    showBackground = true,
    widthDp = 412,
    heightDp = 500,
)
@Composable
fun WorldMapArtworkPreview() {
    PreviewSurface {
        MapArtwork(
            scope = MapScope.WORLD,
            visitedCountryCodes = previewVisitedCountries,
        )
    }
}
