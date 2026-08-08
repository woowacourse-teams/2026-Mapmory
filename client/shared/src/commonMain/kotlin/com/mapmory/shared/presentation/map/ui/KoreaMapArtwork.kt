package com.mapmory.shared.presentation.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.ProvincePolygon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

@Composable
fun KoreaMapArtwork(
    visitedRegionCodes: Set<String> = emptySet(),
    onRegionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val provinces = remember { GeneratedKoreaMapData.provinces }
    val bounds = remember(provinces) { KoreaBounds.from(provinces) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnRegionClick by rememberUpdatedState(onRegionClick)
    val projection = remember(bounds, viewportSize) {
        KoreaProjection.from(bounds, viewportSize)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07171B))
            .onSizeChanged { viewportSize = it }
            .pointerInput(projection) {
                detectTapGestures { position ->
                    provinces.firstOrNull { province ->
                        province.rings.any { ring ->
                            pointInScreenRing(
                                point = position,
                                ring = ring.map(projection::project),
                            )
                        }
                    }?.let { currentOnRegionClick(it.code) }
                }
            },
    ) {
        if (!projection.isValid) return@Canvas
        val outlineWidth = max(0.8f, size.minDimension * 0.0035f)

        provinces.forEach { province ->
            val isVisited = province.code in visitedRegionCodes
            val fillColor = if (isVisited) Color(0xFF55D5A0) else Color(0xFF303B4D)
            val outlineColor = if (isVisited) Color(0xFF9AF0C5) else Color(0xFF7B879B)

            province.rings.forEach { ring ->
                if (ring.size < 3) return@forEach
                val path = Path().apply {
                    ring.forEachIndexed { index, point ->
                        val screen = projection.project(point)
                        if (index == 0) moveTo(screen.x, screen.y) else lineTo(screen.x, screen.y)
                    }
                    close()
                }
                drawPath(path = path, color = fillColor)
                drawPath(
                    path = path,
                    color = outlineColor,
                    style = Stroke(width = outlineWidth),
                )
            }
        }
    }
}

private data class KoreaProjection(
    private val bounds: KoreaBounds,
    private val longitudeFactor: Float,
    private val scale: Float,
    private val left: Float,
    private val top: Float,
    val isValid: Boolean,
) {
    fun project(point: GeoPoint): Offset = Offset(
        x = left + (point.longitude - bounds.minLongitude) * longitudeFactor * scale,
        y = top + (bounds.maxLatitude - point.latitude) * scale,
    )

    companion object {
        fun from(bounds: KoreaBounds, viewportSize: IntSize): KoreaProjection {
            val longitudeSpan = bounds.maxLongitude - bounds.minLongitude
            val latitudeSpan = bounds.maxLatitude - bounds.minLatitude
            if (viewportSize.width <= 0 || viewportSize.height <= 0 || longitudeSpan <= 0f || latitudeSpan <= 0f) {
                return KoreaProjection(bounds, 1f, 0f, 0f, 0f, false)
            }

            // Longitude degrees are physically shorter than latitude degrees in Korea.
            // Applying the center-latitude factor keeps province silhouettes from
            // looking stretched horizontally while retaining the source coordinates.
            val referenceLatitudeRadians =
                (bounds.minLatitude + bounds.maxLatitude) / 2f * PI.toFloat() / 180f
            val longitudeFactor = cos(referenceLatitudeRadians).coerceAtLeast(0.1f)
            val projectedLongitudeSpan = longitudeSpan * longitudeFactor
            val width = viewportSize.width.toFloat()
            val height = viewportSize.height.toFloat()
            val horizontalPadding = width * 0.08f
            val verticalPadding = height * 0.06f
            val scale = min(
                (width - horizontalPadding * 2f) / projectedLongitudeSpan,
                (height - verticalPadding * 2f) / latitudeSpan,
            )
            val mapWidth = projectedLongitudeSpan * scale
            val mapHeight = latitudeSpan * scale
            val left = (width - mapWidth) / 2f
            val top = ((height - mapHeight) / 2f - height * 0.07f)
                .coerceAtLeast(verticalPadding)
            return KoreaProjection(bounds, longitudeFactor, scale, left, top, true)
        }
    }
}

private fun pointInScreenRing(point: Offset, ring: List<Offset>): Boolean {
    if (ring.size < 3) return false

    var inside = false
    var previous = ring.last()
    ring.forEach { current ->
        val crossesY = (current.y > point.y) != (previous.y > point.y)
        if (crossesY) {
            val intersectionX =
                (previous.x - current.x) *
                    (point.y - current.y) /
                    (previous.y - current.y) + current.x
            if (point.x < intersectionX) inside = !inside
        }
        previous = current
    }
    return inside
}

private data class KoreaBounds(
    val minLongitude: Float,
    val maxLongitude: Float,
    val minLatitude: Float,
    val maxLatitude: Float,
) {
    companion object {
        fun from(provinces: List<ProvincePolygon>): KoreaBounds {
            val points = provinces.flatMap { province -> province.rings.flatten() }
            return KoreaBounds(
                minLongitude = points.minOf(GeoPoint::longitude),
                maxLongitude = points.maxOf(GeoPoint::longitude),
                minLatitude = points.minOf(GeoPoint::latitude),
                maxLatitude = points.maxOf(GeoPoint::latitude),
            )
        }
    }
}
