package com.mapmory.shared.presentation.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mapmory.shared.presentation.map.data.GeneratedWorldMapData
import com.mapmory.shared.presentation.map.domain.CountryPolygon
import com.mapmory.shared.presentation.map.math.Quaternion
import com.mapmory.shared.presentation.map.math.Vec3
import com.mapmory.shared.presentation.map.math.clipToFrontHemisphere
import com.mapmory.shared.presentation.map.math.projectToScreen
import com.mapmory.shared.presentation.map.math.toSphere
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.min

@Composable
internal fun WorldGlobe(
    visitedCountryCodes: Set<String>,
    onCountryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var longitude by rememberSaveable { mutableStateOf(InitialLongitude) }
    var latitude by rememberSaveable { mutableStateOf(0f) }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    val currentOnCountryClick by rememberUpdatedState(onCountryClick)

    // Keep the globe roll-free: longitude spin plus bounded latitude tilt only.
    val rotation = remember(longitude, latitude) {
        val polarRotation = Quaternion.fromAxisAngle(
            axis = Vec3(0f, 1f, 0f),
            angle = longitude,
        )
        val tiltRotation = Quaternion.fromAxisAngle(
            axis = Vec3(1f, 0f, 0f),
            angle = latitude,
        )
        (tiltRotation * polarRotation).normalized()
    }

    val countries = remember { GeneratedWorldMapData.countries }
    val baseRings = remember(countries) {
        countries.flatMap { country ->
            country.rings.map { ring ->
                country to ring.map { point -> point.toSphere() }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .pointerInput(viewportSize) {
                detectTransformGestures(
                    panZoomLock = true,
                ) { _, pan, zoomChange, _ ->
                    val radius = min(
                        viewportSize.width.toFloat(),
                        viewportSize.height.toFloat(),
                    ) * 0.42f * zoom
                    if (radius <= 0f) return@detectTransformGestures

                    // Horizontal drag spins around the North–South polar axis.
                    longitude += pan.x / radius * RotationSensitivity
                    // Vertical drag only tilts within a predictable latitude range.
                    latitude = (latitude + pan.y / radius * RotationSensitivity)
                        .coerceIn(-MaxTilt, MaxTilt)

                    // zoomChange is > 1 for pinch-out and < 1 for pinch-in.
                    zoom = (zoom * zoomChange).coerceIn(MinZoom, MaxZoom)
                }
            }
            .pointerInput(viewportSize, longitude, latitude, zoom) {
                detectTapGestures { position ->
                    val globeRadius = min(
                        viewportSize.width.toFloat(),
                        viewportSize.height.toFloat(),
                    ) * 0.42f * zoom
                    countryAtScreenPoint(
                        position = position,
                        center = Offset(viewportSize.width / 2f, viewportSize.height / 2f),
                        radius = globeRadius,
                        rotation = rotation,
                        countries = countries,
                    )?.let { currentOnCountryClick(it.code) }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val globeRadius = min(size.width, size.height) * 0.42f * zoom
            val center = Offset(size.width / 2f, size.height / 2f)

            drawRect(color = Color(0xFF121518))

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.82f to Color.Transparent,
                        0.91f to Color(0xFF7F9ABA).copy(alpha = 0.055f),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = globeRadius * 1.12f,
                ),
                radius = globeRadius * 1.12f,
                center = center,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A3747),
                        Color(0xFF1B2533),
                        Color(0xFF111923),
                    ),
                    center = center - Offset(globeRadius * 0.28f, globeRadius * 0.28f),
                    radius = globeRadius * 1.2f,
                ),
                radius = globeRadius,
                center = center,
            )

            baseRings.forEach { (country, points) ->
                val rotated = points.map(rotation::rotate)
                val clipped = clipToFrontHemisphere(rotated)
                if (clipped.size < 3) return@forEach

                val path = Path()
                clipped.forEachIndexed { index, point ->
                    val screen = projectToScreen(point, center, globeRadius)
                    if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
                }
                path.close()

                val isVisited = country.code in visitedCountryCodes
                drawPath(
                    path = path,
                    color = if (isVisited) Color(0xFF35C987) else Color(0xFF2B3546),
                )
                drawPath(
                    path = path,
                    color = if (isVisited) {
                        Color(0xFF8AEBC1).copy(alpha = 0.82f)
                    } else {
                        Color(0xFF7C8FAA).copy(alpha = 0.54f)
                    },
                    style = Stroke(width = if (isVisited) 1.25.dp.toPx() else 0.85.dp.toPx()),
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.09f),
                        0.42f to Color.White.copy(alpha = 0.025f),
                        1f to Color.Transparent,
                    ),
                    center = center - Offset(globeRadius * 0.42f, globeRadius * 0.42f),
                    radius = globeRadius * 1.15f,
                ),
                radius = globeRadius,
                center = center,
            )

        }
    }
}

private val InitialLongitude = -127f * PI.toFloat() / 180f
private const val RotationSensitivity = 1.35f
private const val MaxTilt = 1.3f
private const val MinZoom = 0.75f
private const val MaxZoom = 8f


private fun countryAtScreenPoint(
    position: Offset,
    center: Offset,
    radius: Float,
    rotation: Quaternion,
    countries: List<CountryPolygon>,
): CountryPolygon? {
    if (radius <= 0f) return null

    val x = (position.x - center.x) / radius
    val y = -(position.y - center.y) / radius
    val distanceSquared = x * x + y * y
    if (distanceSquared > 1f) return null

    val screenPoint = Vec3(
        x = x,
        y = y,
        z = kotlin.math.sqrt((1f - distanceSquared).coerceAtLeast(0f)),
    )
    val geoPoint = rotation.conjugate().rotate(screenPoint)
    val longitude = atan2(geoPoint.x, geoPoint.z) * 180f / PI.toFloat()
    val latitude = asin(geoPoint.y.coerceIn(-1f, 1f)) * 180f / PI.toFloat()

    return countries.firstOrNull { country ->
        country.rings.any { ring -> pointInGeoRing(longitude, latitude, ring) }
    }
}

private fun pointInGeoRing(
    longitude: Float,
    latitude: Float,
    ring: List<com.mapmory.shared.presentation.map.domain.GeoPoint>,
): Boolean {
    if (ring.size < 3) return false

    var inside = false
    var previous = ring.last()
    ring.forEach { current ->
        val crossesLatitude = (current.latitude > latitude) != (previous.latitude > latitude)
        if (crossesLatitude) {
            val intersectionLongitude =
                (previous.longitude - current.longitude) *
                    (latitude - current.latitude) /
                    (previous.latitude - current.latitude) + current.longitude
            if (longitude < intersectionLongitude) inside = !inside
        }
        previous = current
    }
    return inside
}
