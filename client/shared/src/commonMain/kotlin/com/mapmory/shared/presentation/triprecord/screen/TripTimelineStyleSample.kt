package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.domain.model.ShareVideoStyle
import com.mapmory.shared.presentation.map.data.GeneratedWorldMapData
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.math.Quaternion
import com.mapmory.shared.presentation.map.math.Vec3
import com.mapmory.shared.presentation.map.math.clipToFrontHemisphere
import com.mapmory.shared.presentation.map.math.projectToScreen
import com.mapmory.shared.presentation.map.math.toSphere
import kotlin.math.PI
import kotlin.math.acos

private val sampleStops = listOf(
    GeoPoint(latitude = 37.5665f, longitude = 126.9780f),
    GeoPoint(latitude = 35.6762f, longitude = 139.6503f),
    GeoPoint(latitude = 48.8566f, longitude = 2.3522f),
)
private val sampleCountries = setOf("KR", "JP", "FR")
private val routeColor = Color(0xFF9BE9CB)

@Composable
internal fun TripTimelineStyleSample(style: ShareVideoStyle) {
    val progress = remember(style) { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(2f, tween(3_200, easing = LinearEasing)) }
    val background = when (style) {
        ShareVideoStyle.CLASSIC -> Color(0xFF184336)
        ShareVideoStyle.ZOOM -> Color(0xFF1A4C51)
        ShareVideoStyle.ORBIT -> Color(0xFF232C50)
        ShareVideoStyle.HORIZON -> Color(0xFF234A69)
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(background, Color(0xFF101C20)))),
    ) {
        Text(
            style.previewLabel,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
        )
        Spacer(
            Modifier.fillMaxWidth().aspectRatio(1.8f).drawWithCache {
                val radians = PI.toFloat() / 180f
                // Keep Seoul, Tokyo and Paris on the visible hemisphere of every style.
                val rotation = Quaternion.fromAxisAngle(Vec3(1f, 0f, 0f), 57f * radians) *
                    Quaternion.fromAxisAngle(Vec3(0f, 1f, 0f), -71f * radians)
                val horizon = style == ShareVideoStyle.HORIZON
                val radius = when (style) {
                    ShareVideoStyle.CLASSIC, ShareVideoStyle.ORBIT -> size.height * 0.47f
                    ShareVideoStyle.ZOOM -> size.height * 0.63f
                    ShareVideoStyle.HORIZON -> size.height * 0.82f
                }
                val center = Offset(size.width / 2f, size.height * if (horizon) 0.84f else 0.52f)
                val land = GeneratedWorldMapData.countries.flatMap { country ->
                    country.rings.mapNotNull { ring ->
                        val front = clipToFrontHemisphere(ring.map { rotation.rotate(it.toSphere()) })
                        if (front.size < 3) return@mapNotNull null
                        val path = Path().apply {
                            front.forEachIndexed { index, point ->
                                val pixel = projectToScreen(point, center, radius)
                                if (index == 0) moveTo(pixel.x, pixel.y) else lineTo(pixel.x, pixel.y)
                            }
                            close()
                        }
                        path to (country.code in sampleCountries)
                    }
                }
                val stops = sampleStops.map { rotation.rotate(it.toSphere()) }
                val legs = stops.zipWithNext().map { (from, to) ->
                    val axis = from.cross(to).normalized()
                    val angle = acos(from.dot(to).coerceIn(-1f, 1f))
                    val projectLeg: (Float) -> Offset = { fraction ->
                        val point = Quaternion.fromAxisAngle(axis, angle * fraction).rotate(from)
                        projectToScreen(point, center, radius)
                    }
                    projectLeg
                }
                val route = Path().apply {
                    val first = legs.first()(0f)
                    moveTo(first.x, first.y)
                    legs.forEach { leg ->
                        for (step in 1..32) {
                            val point = leg(step / 32f)
                            lineTo(point.x, point.y)
                        }
                    }
                }
                val markers = stops.map { projectToScreen(it, center, radius) }
                val ocean = Brush.radialGradient(
                    listOf(background, Color(0xFF101C20)),
                    center = center - Offset(radius * 0.28f, radius * 0.32f),
                    radius = radius * 1.4f,
                )

                onDrawBehind {
                    if (style == ShareVideoStyle.ORBIT) repeat(24) { index ->
                        val x = size.width * (index * 43 % 97 + 1) / 100f
                        val y = size.height * (index * 71 % 91 + 4) / 100f
                        drawCircle(Color.White.copy(alpha = 0.45f), 0.8.dp.toPx(), Offset(x, y))
                    }
                    drawCircle(ocean, radius, center)
                    land.forEach { (path, visited) ->
                        drawPath(path, if (visited) Color(0xFF447B70) else Color(0xFF34545B))
                        drawPath(path, Color(0xFF66838B), style = Stroke(0.5.dp.toPx()))
                    }
                    drawCircle(Color(0xFF66838B), radius, center, style = Stroke(0.75.dp.toPx()))
                    drawPath(route, routeColor, style = Stroke(1.5.dp.toPx()))
                    markers.forEach { drawCircle(routeColor, 2.5.dp.toPx(), it) }
                    val current = progress.value
                    val legIndex = current.toInt().coerceAtMost(legs.lastIndex)
                    drawCircle(Color.White, 3.5.dp.toPx(), legs[legIndex](current - legIndex))
                }
            },
        )
        Text(
            "예시 경로  서울 → 도쿄 → 파리",
            color = routeColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp),
        )
    }
}
