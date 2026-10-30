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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiModel
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiState
import mapmoryclient.shared.generated.resources.Res
import mapmoryclient.shared.generated.resources.mori_expression_sheet
import org.jetbrains.compose.resources.painterResource
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

internal fun tripAchievementBadges(statistics: TripStatistics): List<TripAchievementBadge> =
    tripAchievementBadges(
        TripStatisticsUiModel.Empty.copy(
            recordCount = statistics.recordCount.toUiCount(),
            worldVisitedCount = statistics.visitedCountryCount.toUiCount(),
            koreaVisitedCount = statistics.visitedKoreaDistrictCount.toUiCount(),
            visitedCountryCodes = statistics.visitedCountryCodes.toSet(),
        ),
    )

internal fun newlyEarnedTripAchievementBadges(
    previous: TripStatistics,
    current: TripStatistics,
): List<TripAchievementBadge> {
    val previouslyEarned = tripAchievementBadges(previous).filter { it.earned }.mapTo(mutableSetOf()) { it.id }
    return tripAchievementBadges(current).filter { it.earned && it.id !in previouslyEarned }
}

internal fun tripAchievementMoriExpressionIndex(earnedCount: Int, totalCount: Int): Int = when {
    totalCount <= 0 -> 0
    earnedCount <= 0 -> 2
    earnedCount * 4 < totalCount -> 0
    earnedCount * 2 < totalCount -> 4
    earnedCount * 4 < totalCount * 3 -> 3
    else -> 1
}

@Composable
internal fun TripAchievementCollection(
    statistics: TripStatisticsUiModel,
    onShowAll: () -> Unit,
) {
    val badges = tripAchievementBadges(statistics)
    var selectedBadgeId by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleBadges = badges.filter { it.progressUnit == "개" }.take(2) +
        badges.filter { it.progressUnit == "곳" }.take(2)

    StatsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("여행 업적", color = TripRecordPalette.current.headingText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "기록을 남기고 여행 배지를 모아요",
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
            TextButton(onClick = onShowAll) {
                Text("전체 업적 보기 (${badges.size})  →", fontSize = 12.sp)
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
internal fun TripAchievementScreen(
    statisticsUiState: TripStatisticsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBadgeId by rememberSaveable { mutableStateOf<String?>(null) }
    TripRecordBackground(modifier, TripRecordPalette.current.pageBackground) {
        Column(Modifier.fillMaxSize().background(TripRecordPalette.current.pageBackground)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TripIconButton(label = "←", contentDescription = "뒤로가기", onClick = onBack)
                Text(
                    "여행 업적",
                    color = TripRecordPalette.current.headingText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            when (statisticsUiState) {
                TripStatisticsUiState.Loading -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("여행 업적을 불러오고 있어요.", color = TripRecordPalette.current.secondaryText)
                }

                is TripStatisticsUiState.Error -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(statisticsUiState.message, color = TripRecordPalette.current.secondaryText)
                        TextButton(onClick = onRetry) {
                            Text("다시 시도", color = TripRecordPalette.current.primary)
                        }
                    }
                }

                is TripStatisticsUiState.Success -> {
                    val statistics = statisticsUiState.statistics
                    val badges = tripAchievementBadges(statistics)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        AchievementCompanionCard(badges)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(badges.chunked(2)) { row ->
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
                    badges.firstOrNull { it.id == selectedBadgeId }?.let { badge ->
                        if (badge.earned) {
                            TripAchievementSharePreviewDialog(badge) { selectedBadgeId = null }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCompanionCard(badges: List<TripAchievementBadge>) {
    val palette = TripRecordPalette.current
    val earnedCount = badges.count { it.earned }
    val expression = tripAchievementMoriExpressionIndex(earnedCount, badges.size)
    val message = when (expression) {
        0 -> "모리와 첫 여행 이야기를 기다려요"
        1 -> "모리와 여행 업적을 모두 모았어요!"
        2 -> "첫 기록을 남기고 함께 출발해요"
        3 -> "기록이 차곡차곡 쌓이고 있어요"
        else -> "다음 여행 이야기는 어디일까요?"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = palette.primarySoft),
        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.16f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoriExpressionImage(
                expressionIndex = expression,
                description = "여행 업적 진행 상황을 보여주는 모리",
                modifier = Modifier.height(92.dp).aspectRatio(0.6f),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "MAPMORY  /  TRAVEL ACHIEVEMENTS",
                    color = palette.secondaryAccent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    message,
                    color = palette.headingText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Text(
                    "$earnedCount / ${badges.size}개 획득",
                    color = palette.secondaryAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 5.dp),
                )
                Box(Modifier.fillMaxWidth().height(5.dp).background(palette.surface, CircleShape)) {
                    Box(
                        Modifier.fillMaxWidth(earnedCount.toFloat() / badges.size)
                            .height(5.dp).background(palette.primary, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TripAchievementUnlockDialog(
    badges: List<TripAchievementBadge>,
    onDismiss: () -> Unit,
) {
    val palette = TripRecordPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth().background(palette.primarySoft, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MoriExpressionImage(
                        expressionIndex = 1,
                        description = "새 업적을 축하하는 모리",
                        modifier = Modifier.height(82.dp).aspectRatio(0.6f),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "MAPMORY  /  TRAVEL BADGE",
                            color = palette.secondaryAccent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                        )
                        Text(
                            if (badges.size == 1) "새 여행 업적을 달성했어요!" else "새 여행 업적 ${badges.size}개를 달성했어요!",
                            color = palette.headingText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Text(
                    "기록을 남기며 새로운 배지를 모았어요.",
                    color = palette.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(badges) { badge ->
                        Row(
                            Modifier.fillMaxWidth().background(palette.surfaceElevated, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TripAchievementBadgeArtwork(badge.id, earned = true, modifier = Modifier.size(46.dp))
                            Column {
                                Text(badge.title, color = palette.headingText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(badge.requirement, color = palette.secondaryText, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) {
                    Text("계속하기", color = palette.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MoriExpressionImage(
    expressionIndex: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    val painter = painterResource(Res.drawable.mori_expression_sheet)
    Canvas(modifier.clipToBounds().semantics { contentDescription = description }) {
        val frameWidth = size.width
        val frameHeight = size.height
        clipRect {
            translate(left = -frameWidth * expressionIndex.coerceIn(0, 4)) {
                with(painter) {
                    draw(size = Size(frameWidth * 5f, frameHeight))
                }
            }
        }
    }
}

private fun Long.toUiCount(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

@Composable
private fun AchievementTile(
    badge: TripAchievementBadge,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    Card(
        modifier = modifier.height(248.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (badge.earned) palette.primary.copy(alpha = 0.30f) else palette.border),
        colors = CardDefaults.cardColors(containerColor = if (badge.earned) palette.primarySoft else palette.surfaceElevated),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TripAchievementBadgeArtwork(
                badgeId = badge.id,
                earned = badge.earned,
                modifier = Modifier.size(90.dp),
            )
            Text(
                badge.title,
                color = palette.headingText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
            Text(
                badge.requirement,
                color = palette.secondaryText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (badge.earned) "달성 완료" else "진행 중",
                    color = if (badge.earned) palette.primary else palette.secondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${badge.progress} / ${badge.target}${badge.progressUnit}",
                    color = palette.secondaryText,
                    fontSize = 10.sp,
                )
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 5.dp).height(5.dp)
                    .background(palette.border, CircleShape),
            ) {
                Box(
                    Modifier.fillMaxWidth((badge.progress.toFloat() / badge.target).coerceIn(0f, 1f))
                        .height(5.dp).background(if (badge.earned) palette.primary else palette.secondaryAccent, CircleShape),
                )
            }
            Spacer(Modifier.weight(1f))
            if (badge.earned) {
                Button(
                    onClick = onShareClick,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primary,
                        contentColor = palette.onPrimary,
                    ),
                ) {
                    Text("업적 공유", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "다음 배지까지 조금만 더",
                    color = palette.secondaryText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(38.dp).padding(top = 8.dp),
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
            val colors = badgePalette(earned)
            val side = minOf(size.width, size.height)
            val radius = side * 0.31f
            val center = Offset(size.width / 2f, size.height * 0.46f)
            val shellRadius = radius * 1.08f

            drawBadgeLeaf(center, radius, side = -1f, color = colors.leafLeft)
            drawBadgeLeaf(center, radius, side = 1f, color = colors.leafRight)
            drawCircle(
                color = (if (earned) Color(0xFF193B2A) else Color.Black).copy(alpha = if (earned) 0.16f else 0.07f),
                radius = shellRadius,
                center = Offset(center.x, center.y + 2.dp.toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors.outerLight, colors.outer, colors.outerDark),
                    center = Offset(center.x - radius * 0.32f, center.y - radius * 0.42f),
                    radius = shellRadius * 1.7f,
                ),
                radius = shellRadius,
                center = center,
            )
            drawCircle(
                Color.White.copy(alpha = if (earned) 0.58f else 0.42f),
                shellRadius,
                center,
                style = Stroke(1.3.dp.toPx()),
            )
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
        }
        Text(
            text = target,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
                .background(if (earned) badgeAccent(badgeId) else Color(0xFF777777), CircleShape)
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
    val leafLeft: Color,
    val leafRight: Color,
    val sparkle: Color,
)

internal fun badgePalette(earned: Boolean): BadgeArtPalette = if (!earned) {
    BadgeArtPalette(
        outerLight = Color(0xFFE9E9E9), outer = Color(0xFFC1C1C1), outerDark = Color(0xFF909090),
        faceLight = Color.White, face = Color(0xFFE6E6E6), faceDark = Color(0xFFD1D1D1),
        rim = Color(0xFFB8B8B8), ink = Color(0xFF656565), bright = Color(0xFF888888),
        leafLeft = Color(0xFF888888), leafRight = Color(0xFFB3B3B3), sparkle = Color(0xFF777777),
    )
} else {
    BadgeArtPalette(
        outerLight = Color(0xFFAED7BB), outer = Color(0xFF6FA582), outerDark = Color(0xFF367150),
        faceLight = Color(0xFFFFF9E8), face = Color(0xFFF4E6C2), faceDark = Color(0xFFE5D09B),
        rim = Color(0xFFF7EDCF), ink = Color(0xFF28543D), bright = Color(0xFFF2BD4E),
        leafLeft = Color(0xFF255D3C), leafRight = Color(0xFF4B8A5E), sparkle = Color(0xFFE8754F),
    )
}

private fun badgeAccent(id: String): Color = if (id == "first_record" || id.startsWith("records_")) Color(0xFFC94C57) else Color(0xFF4D9272)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBadgeLeaf(
    center: Offset,
    radius: Float,
    side: Float,
    color: Color,
) {
    val leaf = Path().apply {
        moveTo(center.x + side * radius * 0.08f, center.y - radius * 0.82f)
        cubicTo(
            center.x + side * radius * 0.44f, center.y - radius * 0.90f,
            center.x + side * radius * 0.63f, center.y - radius * 1.24f,
            center.x + side * radius * 0.22f, center.y - radius * 1.40f,
        )
        cubicTo(
            center.x + side * radius * 0.01f, center.y - radius * 1.42f,
            center.x - side * radius * 0.08f, center.y - radius * 1.05f,
            center.x + side * radius * 0.08f, center.y - radius * 0.82f,
        )
        close()
    }
    drawPath(leaf, color)
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
    drawPath(pin, colors.sparkle)
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
