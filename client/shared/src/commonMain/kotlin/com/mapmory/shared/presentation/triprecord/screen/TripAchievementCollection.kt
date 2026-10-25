package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class TripAchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val requirement: String,
    val progress: Int,
    val target: Int,
    val progressUnit: String,
) {
    val earned: Boolean get() = progress >= target
}

internal fun tripAchievementBadges(statistics: TripStatisticsUiModel): List<TripAchievementBadge> {
    // The API includes KR in the country count and reports Korean districts separately.
    val distinctRegionCount = (statistics.koreaVisitedCount + statistics.worldVisitedCount -
        if ("KR" in statistics.visitedCountryCodes) 1 else 0).coerceAtLeast(0)
    return listOf(
        1 to "첫 여행 기록", 5 to "다섯 번의 추억", 10 to "열 번의 여행",
        25 to "차곡차곡 여행가", 50 to "추억 수집가", 100 to "백 번의 이야기",
    ).map { (target, title) ->
        TripAchievementBadge(
            id = if (target == 1) "first_record" else "records_$target",
            title = title,
            description = "여행 기록 ${target}개를 남겼어요.",
            requirement = "여행 기록 ${target}개",
            progress = statistics.recordCount.coerceIn(0, target),
            target = target,
            progressUnit = "개",
        )
    } + listOf(
        2 to "두 곳의 기록", 5 to "발걸음이 넓어져요", 10 to "열 곳의 발자국",
        25 to "여행 반경 확장", 50 to "넓은 세상 수집가", 100 to "백 곳의 발자국",
    ).map { (target, title) ->
        TripAchievementBadge(
            id = if (target == 2) "two_regions" else "regions_$target",
            title = title,
            description = "서로 다른 지역 ${target}곳에 기록을 남겼어요.",
            requirement = "서로 다른 지역 ${target}곳",
            progress = distinctRegionCount.coerceIn(0, target),
            target = target,
            progressUnit = "곳",
        )
    }
}

@Composable
internal fun TripAchievementCollection(statistics: TripStatisticsUiModel) {
    val badges = tripAchievementBadges(statistics)
    var selectedBadgeId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllBadges by rememberSaveable { mutableStateOf(false) }
    val visibleBadges = if (showAllBadges) badges else {
        badges.filter { it.progressUnit == "개" }.take(2) + badges.filter { it.progressUnit == "곳" }.take(2)
    }

    StatsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("여행 업적", color = TripRecordPalette.current.headingText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "여행 기록으로 배지를 모아요",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box(
                Modifier.background(TripRecordPalette.current.primarySoft, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "${badges.count { it.earned }} / ${badges.size} 획득",
                    color = TripRecordPalette.current.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            visibleBadges.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { badge ->
                        AchievementTile(
                            badge = badge,
                            onShareClick = { selectedBadgeId = badge.id },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { showAllBadges = !showAllBadges }) {
                Text(if (showAllBadges) "접기" else "전체 보기 (${badges.size})", fontSize = 12.sp)
            }
        }
    }

    badges.firstOrNull { it.id == selectedBadgeId }?.let { badge ->
        if (badge.earned) {
            TripAchievementSharePreviewDialog(badge) { selectedBadgeId = null }
        }
    }
}

@Composable
private fun AchievementTile(
    badge: TripAchievementBadge,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    Card(
        modifier = modifier.height(248.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (badge.earned) badgeAccent(badge.id).copy(alpha = 0.38f) else palette.border),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceElevated),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TripAchievementBadgeArtwork(
                badgeId = badge.id,
                earned = badge.earned,
                modifier = Modifier.size(86.dp),
            )
            Text(
                badge.title,
                color = palette.headingText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            Text(
                badge.requirement,
                color = palette.secondaryText,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (badge.earned) "달성 완료" else "진행 중",
                    color = if (badge.earned) badgeAccent(badge.id) else palette.secondaryText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${badge.progress} / ${badge.target}${badge.progressUnit}",
                    color = palette.secondaryText,
                    fontSize = 9.sp,
                )
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)
                    .background(palette.border, CircleShape),
            ) {
                Box(
                    Modifier.fillMaxWidth(badge.progress.toFloat() / badge.target)
                        .height(4.dp).background(badgeAccent(badge.id), CircleShape),
                )
            }
            Spacer(Modifier.weight(1f))
            if (badge.earned) {
                Button(
                    onClick = onShareClick,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = badgeAccent(badge.id),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("업적 공유", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "다음 배지까지 조금만 더",
                    color = palette.secondaryText,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(36.dp).padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun TripAchievementBadgeArtwork(
    badgeId: String,
    earned: Boolean,
    modifier: Modifier = Modifier,
) {
    val recordBadge = badgeId == "first_record" || badgeId.startsWith("records_")
    val target = when (badgeId) {
        "first_record" -> "1"
        "two_regions" -> "2"
        else -> badgeId.substringAfterLast('_')
    }
    Box(
        modifier.semantics {
            contentDescription = "${if (recordBadge) "여행 기록 ${target}개" else "서로 다른 지역 ${target}곳"} 배지, ${if (earned) "획득" else "미획득"}"
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val colors = badgePalette(badgeId, earned)
            val side = minOf(size.width, size.height)
            val radius = side * 0.345f
            val center = Offset(size.width / 2f, size.height * 0.39f)
            val ribbonTop = center.y + radius * 0.48f
            val ribbonBottom = size.height * 0.94f
            val ribbonHalfWidth = radius * 0.28f

            drawPath(
                ribbonPath(center.x, ribbonTop, ribbonBottom, ribbonHalfWidth, left = true),
                color = colors.ribbonLeft,
            )
            drawPath(
                ribbonPath(center.x, ribbonTop, ribbonBottom, ribbonHalfWidth, left = false),
                color = colors.ribbonRight,
            )

            val shadow = rosettePath(center.x, center.y + 2.dp.toPx(), radius * 1.12f)
            drawPath(shadow, (if (earned) Color(0xFF14231D) else Color.Black).copy(alpha = if (earned) 0.20f else 0.08f))

            val rosette = rosettePath(center.x, center.y, radius * 1.12f)
            drawPath(
                path = rosette,
                brush = Brush.sweepGradient(
                    listOf(colors.outerLight, colors.outer, colors.outerDark, colors.outerLight),
                    center = center,
                ),
            )
            drawPath(rosette, Color.White.copy(alpha = if (earned) 0.75f else 0.48f), style = Stroke(1.5.dp.toPx()))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors.faceLight, colors.face, colors.faceDark),
                    center = Offset(center.x - radius * 0.24f, center.y - radius * 0.28f),
                    radius = radius * 1.8f,
                ),
                radius = radius * 0.83f,
                center = center,
            )
            drawCircle(colors.rim, radius * 0.73f, center, style = Stroke(1.4.dp.toPx()))

            if (recordBadge) {
                drawFirstRecordSymbol(center, radius, colors)
            } else {
                drawTwoRegionsSymbol(center, radius, colors)
            }

            drawPath(
                starPath(center.x + radius * 0.77f, center.y - radius * 0.52f, radius * 0.20f, radius * 0.08f),
                colors.sparkle,
            )
            drawCircle(colors.sparkle, radius * 0.055f, Offset(center.x - radius * 0.91f, center.y - radius * 0.50f))
            drawCircle(Color.White.copy(alpha = 0.88f), radius * 0.045f, Offset(center.x + radius * 0.91f, center.y + radius * 0.04f))
        }
        Text(
            text = target,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
                .background(if (earned) badgeAccent(badgeId) else Color(0xFF777777), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

internal data class BadgeArtPalette(
    val outerLight: Color,
    val outer: Color,
    val outerDark: Color,
    val faceLight: Color,
    val face: Color,
    val faceDark: Color,
    val rim: Color,
    val ink: Color,
    val bright: Color,
    val ribbonLeft: Color,
    val ribbonRight: Color,
    val sparkle: Color,
)

internal fun badgePalette(id: String, earned: Boolean): BadgeArtPalette = when {
    !earned -> BadgeArtPalette(
        outerLight = Color(0xFFE9E9E9), outer = Color(0xFFC1C1C1), outerDark = Color(0xFF909090),
        faceLight = Color.White, face = Color(0xFFE6E6E6), faceDark = Color(0xFFD1D1D1),
        rim = Color(0xFFB8B8B8), ink = Color(0xFF656565), bright = Color(0xFF888888),
        ribbonLeft = Color(0xFF888888), ribbonRight = Color(0xFFB3B3B3), sparkle = Color(0xFF777777),
    )
    id == "first_record" || id.startsWith("records_") -> BadgeArtPalette(
        outerLight = Color(0xFFFFD86E), outer = Color(0xFFF58B47), outerDark = Color(0xFFDE4C53),
        faceLight = Color(0xFFFFF9D9), face = Color(0xFFFFEAB0), faceDark = Color(0xFFFFC96A),
        rim = Color(0xFFE99D4A), ink = Color(0xFF276B5A), bright = Color(0xFF35A995),
        ribbonLeft = Color(0xFFE64F5B), ribbonRight = Color(0xFFFFA34D), sparkle = Color(0xFFEC5C5A),
    )
    else -> BadgeArtPalette(
        outerLight = Color(0xFF8BE5E4), outer = Color(0xFF5878EA), outerDark = Color(0xFF794BD4),
        faceLight = Color(0xFFE8FFF5), face = Color(0xFFC8F1E7), faceDark = Color(0xFF92DCCB),
        rim = Color(0xFF6680DB), ink = Color(0xFF31569E), bright = Color(0xFFFFB84D),
        ribbonLeft = Color(0xFF4B76D8), ribbonRight = Color(0xFF39B9B0), sparkle = Color(0xFFFFA83D),
    )
}

private fun badgeAccent(id: String): Color = if (id == "first_record" || id.startsWith("records_")) Color(0xFFDE5B52) else Color(0xFF5274D9)

private fun ribbonPath(cx: Float, top: Float, bottom: Float, halfWidth: Float, left: Boolean): Path {
    val direction = if (left) -1f else 1f
    return Path().apply {
        moveTo(cx + direction * halfWidth * 0.18f, top)
        lineTo(cx + direction * halfWidth * 1.75f, top)
        lineTo(cx + direction * halfWidth * 1.72f, bottom)
        lineTo(cx + direction * halfWidth * 0.95f, bottom - halfWidth * 0.44f)
        lineTo(cx + direction * halfWidth * 0.18f, bottom)
        close()
    }
}

private fun rosettePath(cx: Float, cy: Float, radius: Float): Path = Path().apply {
    val vertices = 32
    repeat(vertices) { index ->
        val angle = -PI / 2.0 + index * 2.0 * PI / vertices
        val pointRadius = if (index % 2 == 0) radius else radius * 0.93f
        val x = cx + cos(angle).toFloat() * pointRadius
        val y = cy + sin(angle).toFloat() * pointRadius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun starPath(cx: Float, cy: Float, radius: Float, innerRadius: Float): Path = Path().apply {
    repeat(8) { index ->
        val angle = -PI / 2.0 + index * PI / 4.0
        val pointRadius = if (index % 2 == 0) radius else innerRadius
        val x = cx + cos(angle).toFloat() * pointRadius
        val y = cy + sin(angle).toFloat() * pointRadius
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFirstRecordSymbol(
    center: Offset,
    radius: Float,
    colors: BadgeArtPalette,
) {
    val pin = Path().apply {
        moveTo(center.x, center.y + radius * 0.47f)
        cubicTo(center.x - radius * 0.66f, center.y - radius * 0.12f, center.x - radius * 0.48f, center.y - radius * 0.60f, center.x, center.y - radius * 0.60f)
        cubicTo(center.x + radius * 0.48f, center.y - radius * 0.60f, center.x + radius * 0.66f, center.y - radius * 0.12f, center.x, center.y + radius * 0.47f)
        close()
    }
    drawPath(pin, colors.ink)
    drawCircle(Color.White, radius * 0.22f, Offset(center.x, center.y - radius * 0.19f))
    drawCircle(colors.bright, radius * 0.115f, Offset(center.x, center.y - radius * 0.19f))
    drawPath(
        starPath(center.x + radius * 0.42f, center.y - radius * 0.34f, radius * 0.17f, radius * 0.07f),
        colors.sparkle,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTwoRegionsSymbol(
    center: Offset,
    radius: Float,
    colors: BadgeArtPalette,
) {
    val mapTopLeft = Offset(center.x - radius * 0.52f, center.y - radius * 0.34f)
    val mapSize = androidx.compose.ui.geometry.Size(radius * 1.04f, radius * 0.72f)
    drawRoundRect(
        color = Color.White.copy(alpha = 0.70f),
        topLeft = mapTopLeft,
        size = mapSize,
        cornerRadius = CornerRadius(radius * 0.12f),
    )
    drawLine(
        colors.ink.copy(alpha = 0.30f),
        Offset(center.x - radius * 0.12f, center.y - radius * 0.32f),
        Offset(center.x - radius * 0.12f, center.y + radius * 0.35f),
        strokeWidth = radius * 0.035f,
    )
    drawLine(
        colors.ink.copy(alpha = 0.26f),
        Offset(center.x + radius * 0.20f, center.y - radius * 0.32f),
        Offset(center.x + radius * 0.20f, center.y + radius * 0.35f),
        strokeWidth = radius * 0.035f,
    )
    val route = Path().apply {
        moveTo(center.x - radius * 0.37f, center.y + radius * 0.17f)
        cubicTo(center.x - radius * 0.08f, center.y - radius * 0.38f, center.x + radius * 0.08f, center.y + radius * 0.42f, center.x + radius * 0.39f, center.y - radius * 0.17f)
    }
    drawPath(route, colors.ink, style = Stroke(width = radius * 0.075f, cap = StrokeCap.Round))
    listOf(
        Offset(center.x - radius * 0.37f, center.y + radius * 0.17f) to colors.bright,
        Offset(center.x + radius * 0.39f, center.y - radius * 0.17f) to colors.sparkle,
    ).forEach { (point, color) ->
        drawCircle(Color.White, radius * 0.18f, point)
        drawCircle(color, radius * 0.115f, point)
    }
}
