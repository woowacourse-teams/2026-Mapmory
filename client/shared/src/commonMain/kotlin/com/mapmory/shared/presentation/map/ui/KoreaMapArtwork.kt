package com.mapmory.shared.presentation.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.ProvincePolygon
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewVisitedRegions
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

@Composable
fun KoreaMapArtwork(
    regions: List<ProvincePolygon> = GeneratedKoreaMapData.provinces,
    visitedRegionCodes: Set<String> = emptySet(),
    showRegionLabels: Boolean = false,
    onRegionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bounds = remember(regions) { KoreaBounds.from(regions) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val currentOnRegionClick by rememberUpdatedState(onRegionClick)
    val textMeasurer = rememberTextMeasurer()
    val isDark = LocalMapmoryTheme.current.isDark
    val labelStyle = TextStyle(
        color = if (isDark) Color(0xFF7085A8) else Color(0xFF6B786F),
        fontSize = when {
            regions.size >= 35 -> 7.sp
            regions.size >= 25 -> 8.sp
            else -> 10.sp
        },
        fontWeight = FontWeight.Bold,
    )
    val labelHitPadding = with(LocalDensity.current) { 12.dp.toPx() }
    val projection = remember(bounds, viewportSize) {
        KoreaProjection.from(bounds, viewportSize)
    }
    val preparedRegions = remember(regions, projection) {
        if (!projection.isValid) {
            emptyList()
        } else {
            regions.map { region ->
                PreparedKoreaRegion(
                    region = region,
                    fillPath = Path().apply {
                        region.rings.forEach { ring ->
                            addProjectedRing(ring, projection)
                        }
                    },
                    outlinePath = Path().apply {
                        region.outerEdges().forEach { edge ->
                            val start = projection.project(edge.start)
                            val end = projection.project(edge.end)
                            moveTo(start.x, start.y)
                            lineTo(end.x, end.y)
                        }
                    },
                )
            }
        }
    }
    var zoom by remember(regions) { mutableStateOf(1f) }
    var pan by remember(regions) { mutableStateOf(Offset.Zero) }
    val currentTransform = rememberUpdatedState(MapTransform(zoom, pan))
    val backgroundColor = if (isDark) Color(0xFF121518) else Color(0xFFFAFCFB)
    val visitedFillColor = if (isDark) Color(0xFF35C987) else Color(0xFF4D9272)
    val unvisitedFillColor = if (isDark) Color(0xFF1B2536) else Color(0xFFEDF2EE)
    val visitedOutlineColor = if (isDark) {
        Color(0xFF8AEBC1).copy(alpha = 0.82f)
    } else {
        Color(0xFF2F7659).copy(alpha = 0.72f)
    }
    val unvisitedOutlineColor = if (isDark) Color(0xFF4B5870) else Color(0xFFCAD6CE)
    val preparedLabels = remember(regions, projection, labelStyle, showRegionLabels) {
        if (!showRegionLabels || !projection.isValid) {
            emptyList()
        } else {
            regions.mapNotNull { region ->
                val labelPoint = region.labelPoint() ?: return@mapNotNull null
                PreparedKoreaLabel(
                    region = region,
                    baseCenter = projection.project(labelPoint),
                    layout = textMeasurer.measure(region.name, labelStyle),
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onSizeChanged { viewportSize = it }
            .pointerInput(viewportSize, projection) {
                detectTransformGestures(
                    panZoomLock = true,
                ) { _, panChange, zoomChange, _ ->
                    val transform = currentTransform.value
                    val nextZoom = (transform.zoom * zoomChange).coerceIn(MinZoom, MaxZoom)
                    zoom = nextZoom
                    pan = projection.clampPan(
                        pan = transform.pan + panChange,
                        zoom = nextZoom,
                        viewportSize = viewportSize,
                    )
                }
            }
            .pointerInput(viewportSize, projection, regions, showRegionLabels, preparedLabels) {
                detectTapGestures { position ->
                    val transform = currentTransform.value
                    val mapPoint = projection.unproject(position, transform, viewportSize)
                    val labelRegion = if (showRegionLabels) {
                        preparedLabels.mapNotNull { label ->
                            val labelCenter = transformMapPoint(label.baseCenter, transform, viewportSize)
                            val horizontalDistance = abs(position.x - labelCenter.x)
                            val verticalDistance = abs(position.y - labelCenter.y)
                            if (horizontalDistance <= label.layout.size.width / 2f + labelHitPadding &&
                                verticalDistance <= label.layout.size.height / 2f + labelHitPadding
                            ) {
                                label.region to (horizontalDistance * horizontalDistance + verticalDistance * verticalDistance)
                            } else {
                                null
                            }
                        }.minByOrNull { it.second }?.first
                    } else {
                        null
                    }
                    val tappedRegion = labelRegion ?: regions.regionAt(mapPoint)
                    tappedRegion?.let { currentOnRegionClick(it.code) }
                }
            },
    ) {
        if (!projection.isValid) return@Canvas
        val transform = MapTransform(zoom, pan)
        val outlineWidth = max(0.7f, size.minDimension * 0.0028f)
        val center = Offset(size.width / 2f, size.height / 2f)

        withTransform({
            translate(transform.pan.x, transform.pan.y)
            scale(transform.zoom, transform.zoom, pivot = center)
        }) {
            preparedRegions.forEach { prepared ->
                val isVisited = prepared.region.code in visitedRegionCodes
                val fillColor = if (isVisited) visitedFillColor else unvisitedFillColor
                val outlineColor = if (isVisited) visitedOutlineColor else unvisitedOutlineColor

                drawPath(path = prepared.fillPath, color = fillColor)
                drawPath(
                    path = prepared.outlinePath,
                    color = outlineColor,
                    style = Stroke(width = outlineWidth / transform.zoom),
                )
            }
        }

        // Prototype detail screens keep the map readable by showing the
        // selected province's district names directly on the boundaries.
        preparedLabels.forEach { label ->
            val labelCenter = transformMapPoint(label.baseCenter, transform, viewportSize)
            drawText(
                textLayoutResult = label.layout,
                topLeft = labelCenter - Offset(label.layout.size.width / 2f, label.layout.size.height / 2f),
            )
        }
    }
}

private data class MapTransform(
    val zoom: Float,
    val pan: Offset,
)

private data class PreparedKoreaRegion(
    val region: ProvincePolygon,
    val fillPath: Path,
    val outlinePath: Path,
)

private data class PreparedKoreaLabel(
    val region: ProvincePolygon,
    val baseCenter: Offset,
    val layout: androidx.compose.ui.text.TextLayoutResult,
)

private const val MinZoom = 1f
private const val MaxZoom = 6f
private const val PanSlackFraction = 0.1f
private const val BoundaryEpsilon = 0.000001f

@Composable
fun KoreaMapStatusMessage(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111518)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = message, color = Color(0xFFEAF7F1))
            actionLabel?.let {
                Button(onClick = onAction) { Text(it) }
            }
        }
    }
}

@Preview(
    name = "대한민국 지도 아트워크",
    showBackground = true,
    widthDp = 412,
    heightDp = 500,
)
@Composable
fun KoreaMapArtworkPreview() {
    PreviewSurface {
        KoreaMapArtwork(
            visitedRegionCodes = previewVisitedRegions,
            showRegionLabels = true,
        )
    }
}

@Preview(
    name = "지도 상태 메시지",
    showBackground = true,
    widthDp = 412,
    heightDp = 300,
)
@Composable
fun KoreaMapStatusMessagePreview() {
    KoreaMapStatusMessage(
        message = "시·군·구 지도를 불러오는 중...",
        actionLabel = "다시 시도",
    )
}

private data class KoreaProjection(
    private val bounds: KoreaBounds,
    private val longitudeFactor: Float,
    private val scale: Float,
    private val left: Float,
    private val top: Float,
    private val mapWidth: Float,
    private val mapHeight: Float,
    val isValid: Boolean,
) {
    fun project(point: GeoPoint): Offset = Offset(
        x = left + (point.longitude - bounds.minLongitude) * longitudeFactor * scale,
        y = top + (bounds.maxLatitude - point.latitude) * scale,
    )

    fun unproject(point: Offset, transform: MapTransform, viewportSize: IntSize): GeoPoint {
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        val base = center + (point - center - transform.pan) / transform.zoom
        return GeoPoint(
            longitude = bounds.minLongitude + (base.x - left) / (longitudeFactor * scale),
            latitude = bounds.maxLatitude - (base.y - top) / scale,
        )
    }

    fun clampPan(pan: Offset, zoom: Float, viewportSize: IntSize): Offset = clampKoreaMapPan(
        pan = pan,
        zoom = zoom,
        viewportSize = viewportSize,
        mapLeft = left,
        mapTop = top,
        mapWidth = mapWidth,
        mapHeight = mapHeight,
    )

    companion object {
        fun from(bounds: KoreaBounds, viewportSize: IntSize): KoreaProjection {
            val longitudeSpan = bounds.maxLongitude - bounds.minLongitude
            val latitudeSpan = bounds.maxLatitude - bounds.minLatitude
            if (viewportSize.width <= 0 || viewportSize.height <= 0 || longitudeSpan <= 0f || latitudeSpan <= 0f) {
                return KoreaProjection(bounds, 1f, 0f, 0f, 0f, 0f, 0f, false)
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
            return KoreaProjection(bounds, longitudeFactor, scale, left, top, mapWidth, mapHeight, true)
        }
    }
}

private fun Path.addProjectedRing(ring: List<GeoPoint>, projection: KoreaProjection) {
    if (ring.size < 3) return
    ring.forEachIndexed { index, point ->
        val screen = projection.project(point)
        if (index == 0) moveTo(screen.x, screen.y) else lineTo(screen.x, screen.y)
    }
    close()
}

private fun transformMapPoint(
    base: Offset,
    transform: MapTransform,
    viewportSize: IntSize,
): Offset {
    val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    return center + (base - center) * transform.zoom + transform.pan
}

internal fun clampKoreaMapPan(
    pan: Offset,
    zoom: Float,
    viewportSize: IntSize,
    mapLeft: Float,
    mapTop: Float,
    mapWidth: Float,
    mapHeight: Float,
): Offset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0 || zoom <= 0f) return pan

    val centerX = viewportSize.width / 2f
    val centerY = viewportSize.height / 2f
    val transformedLeft = centerX + (mapLeft - centerX) * zoom
    val transformedRight = centerX + (mapLeft + mapWidth - centerX) * zoom
    val transformedTop = centerY + (mapTop - centerY) * zoom
    val transformedBottom = centerY + (mapTop + mapHeight - centerY) * zoom

    return Offset(
        x = clampMapAxis(
            value = pan.x,
            leading = transformedLeft,
            trailing = transformedRight,
            viewportSize = viewportSize.width.toFloat(),
            slack = viewportSize.width * PanSlackFraction,
        ),
        y = clampMapAxis(
            value = pan.y,
            leading = transformedTop,
            trailing = transformedBottom,
            viewportSize = viewportSize.height.toFloat(),
            slack = viewportSize.height * PanSlackFraction,
        ),
    )
}

private fun clampMapAxis(
    value: Float,
    leading: Float,
    trailing: Float,
    viewportSize: Float,
    slack: Float,
): Float {
    if (trailing - leading <= viewportSize) return value.coerceIn(-slack, slack)
    return value.coerceIn(viewportSize - trailing - slack, -leading + slack)
}

internal fun List<ProvincePolygon>.regionAt(point: GeoPoint): ProvincePolygon? =
    filter { region -> region.rings.any { pointInRing(point, it) } }
        .minByOrNull(ProvincePolygon::area)

private fun pointInRing(point: GeoPoint, ring: List<GeoPoint>): Boolean {
    if (ring.size < 3) return false

    var inside = false
    var previous = ring.last()
    ring.forEach { current ->
        if (pointOnSegment(point, previous, current)) return true
        val crossesY = (current.latitude > point.latitude) != (previous.latitude > point.latitude)
        if (crossesY) {
            val intersectionLongitude =
                (previous.longitude - current.longitude) *
                    (point.latitude - current.latitude) /
                    (previous.latitude - current.latitude) + current.longitude
            if (point.longitude < intersectionLongitude) inside = !inside
        }
        previous = current
    }
    return inside
}

private fun pointOnSegment(point: GeoPoint, start: GeoPoint, end: GeoPoint): Boolean {
    val cross = (point.latitude - start.latitude) * (end.longitude - start.longitude) -
        (point.longitude - start.longitude) * (end.latitude - start.latitude)
    if (kotlin.math.abs(cross) > BoundaryEpsilon) return false
    return point.longitude in minOf(start.longitude, end.longitude)..maxOf(start.longitude, end.longitude) &&
        point.latitude in minOf(start.latitude, end.latitude)..maxOf(start.latitude, end.latitude)
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

private fun ProvincePolygon.labelPoint(): GeoPoint? {
    val largestRing = rings.maxByOrNull { abs(it.signedAreaTwice()) } ?: return null
    val centroid = largestRing.areaAndCentroid()?.let { (_, longitude, latitude) ->
        GeoPoint(longitude.toFloat(), latitude.toFloat())
    }
    if (centroid != null && pointInRing(centroid, largestRing)) return centroid

    val minLongitude = largestRing.minOf(GeoPoint::longitude)
    val maxLongitude = largestRing.maxOf(GeoPoint::longitude)
    val minLatitude = largestRing.minOf(GeoPoint::latitude)
    val maxLatitude = largestRing.maxOf(GeoPoint::latitude)
    val boundsCenter = GeoPoint(
        longitude = (minLongitude + maxLongitude) / 2f,
        latitude = (minLatitude + maxLatitude) / 2f,
    )
    if (pointInRing(boundsCenter, largestRing)) return boundsCenter

    // Concave regions can reject both common representatives. Keep the label on the
    // largest boundary rather than placing it in a different island or in the sea.
    return largestRing.firstOrNull()
}

private fun ProvincePolygon.area(): Double = rings.sumOf { abs(it.signedAreaTwice()) }

private fun List<GeoPoint>.areaAndCentroid(): Triple<Double, Double, Double>? {
    val signedArea = signedAreaTwice()
    if (abs(signedArea) < 0.000001) return null

    var longitude = 0.0
    var latitude = 0.0
    forEachIndexed { index, point ->
        val next = this[(index + 1) % size]
        val cross = point.longitude.toDouble() * next.latitude -
            next.longitude.toDouble() * point.latitude
        longitude += (point.longitude + next.longitude) * cross
        latitude += (point.latitude + next.latitude) * cross
    }
    return Triple(
        abs(signedArea),
        longitude / (3.0 * signedArea),
        latitude / (3.0 * signedArea),
    )
}

private fun List<GeoPoint>.signedAreaTwice(): Double {
    if (size < 3) return 0.0
    return indices.sumOf { index ->
        val point = this[index]
        val next = this[(index + 1) % size]
        point.longitude.toDouble() * next.latitude - next.longitude.toDouble() * point.latitude
    }
}

private data class GeoEdge(
    val start: GeoPoint,
    val end: GeoPoint,
)

private fun ProvincePolygon.outerEdges(): List<GeoEdge> {
    val counts = mutableMapOf<GeoEdge, Int>()
    rings.forEach { ring ->
        val edges = ring.zipWithNext { start, end -> GeoEdge(start, end) }.toMutableList()
        if (ring.size > 2 && ring.first() != ring.last()) edges += GeoEdge(ring.last(), ring.first())
        edges.filter { it.start != it.end }.forEach { edge ->
            val normalized = edge.normalized()
            counts[normalized] = (counts[normalized] ?: 0) + 1
        }
    }
    return counts.filterValues { it == 1 }.keys.toList()
}

private fun GeoEdge.normalized(): GeoEdge = if (
    start.longitude < end.longitude ||
    (start.longitude == end.longitude && start.latitude <= end.latitude)
) {
    this
} else {
    GeoEdge(end, start)
}
