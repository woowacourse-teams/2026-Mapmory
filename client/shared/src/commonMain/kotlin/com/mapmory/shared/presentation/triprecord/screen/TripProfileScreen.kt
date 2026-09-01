package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.PrivacyPolicy
import com.mapmory.shared.presentation.map.data.GeneratedWorldMapData
import com.mapmory.shared.presentation.triprecord.state.TopLocationUiModel
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiModel
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiState
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewStatistics
import kotlin.math.abs
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent

@Composable
fun TripProfileScreen(
    onMapClick: () -> Unit,
    onRecordClick: () -> Unit,
    onCreateClick: () -> Unit,
    onProfileClick: () -> Unit,
    statisticsUiState: TripStatisticsUiState = TripStatisticsUiState.Success(TripStatisticsUiModel.Empty),
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val theme = LocalMapmoryTheme.current
    val analytics = LocalMapmoryAnalytics.current

    TripRecordBackground(modifier = modifier, backgroundColor = TripRecordPalette.current.pageBackground) {
        Column(Modifier.fillMaxSize().background(TripRecordPalette.current.pageBackground)) {
            StatisticsHeader(onSettingsClick = {
                analytics.logEvent(MapmoryAnalyticsEvent.SETTINGS_OPENED)
                showSettings = true
            })

            when (statisticsUiState) {
                TripStatisticsUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TripRecordPalette.current.primary)
                }

                is TripStatisticsUiState.Error -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(statisticsUiState.message, color = TripRecordPalette.current.secondaryText, fontSize = 13.sp)
                }

                is TripStatisticsUiState.Success -> StatisticsContent(
                    statistics = statisticsUiState.statistics,
                    modifier = Modifier.weight(1f),
                )
            }

            TripBottomBar(
                selected = TripBottomTab.PROFILE,
                onMapClick = onMapClick,
                onRecordClick = onRecordClick,
                onCreateClick = onCreateClick,
                onProfileClick = onProfileClick,
                backgroundColor = TripRecordPalette.current.pageBackground,
                dividerColor = TripRecordPalette.current.navigationDivider,
                selectedIconColor = TripRecordPalette.current.primary,
                selectedLabelColor = TripRecordPalette.current.navigationSelectedLabel,
                unselectedColor = TripRecordPalette.current.navigationUnselected,
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = TripRecordPalette.current.surface,
            title = { Text("설정", color = TripRecordPalette.current.headingText) },
            text = {
                Column {
                    Text(
                        text = "화면 테마",
                        color = TripRecordPalette.current.bodyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeOption(
                            label = "라이트 모드",
                            selected = !theme.isDark,
                            onClick = {
                                analytics.logEvent(
                                    MapmoryAnalyticsEvent.THEME_CHANGED,
                                    mapOf("theme" to "light"),
                                )
                                theme.onThemeChange(false)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ThemeOption(
                            label = "다크 모드",
                            selected = theme.isDark,
                            onClick = {
                                analytics.logEvent(
                                    MapmoryAnalyticsEvent.THEME_CHANGED,
                                    mapOf("theme" to "dark"),
                                )
                                theme.onThemeChange(true)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (PrivacyPolicy.URL.isNotBlank()) {
                        Text(
                            text = "서비스 정책은 아래 버튼에서 확인할 수 있어요.",
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                }
            },
            confirmButton = {
                if (PrivacyPolicy.URL.isNotBlank()) {
                    TextButton(onClick = {
                        analytics.logEvent(MapmoryAnalyticsEvent.PRIVACY_POLICY_OPENED)
                        uriHandler.openUri(PrivacyPolicy.URL)
                    }) {
                        Text("개인정보 처리방침", color = TripRecordPalette.current.primary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("닫기", color = TripRecordPalette.current.secondaryText)
                }
            },
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) TripRecordPalette.current.primarySoft else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) TripRecordPalette.current.primary else TripRecordPalette.current.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) TripRecordPalette.current.primary else TripRecordPalette.current.bodyText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatisticsHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "MY TRAVEL DATA",
                color = TripRecordPalette.current.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "여행 통계",
                color = TripRecordPalette.current.headingText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = "⚙",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 21.sp,
            modifier = Modifier.clickable(onClick = onSettingsClick).padding(7.dp),
        )
    }
}

@Composable
private fun StatisticsContent(
    statistics: TripStatisticsUiModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item { PassportCard(statistics) }
        item { VisitProgressCard(statistics) }
        item { RankingCard(statistics.topLocations) }
    }
}

@Composable
private fun PassportCard(statistics: TripStatisticsUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 18.dp, end = 14.dp, bottom = 13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "MAPMORY PASSPORT",
                    color = TripRecordPalette.current.secondaryAccent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = "${statistics.travelerName}의 방문 지도",
                    color = TripRecordPalette.current.headingText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            PassportWorldMap(
                visitedCountryCodes = statistics.visitedCountryCodes,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f),
            )
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(TripRecordPalette.current.primary, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "색칠된 국가는 여행 일지가 있는 곳이에요",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PassportWorldMap(
    visitedCountryCodes: Set<String>,
    modifier: Modifier = Modifier,
) {
    val countries = remember { GeneratedWorldMapData.countries }
    val mapShape = RoundedCornerShape(15.dp)
    val palette = TripRecordPalette.current
    val statisticsPalette = TripStatisticsPalette.current
    Canvas(
        modifier = modifier
            .clip(mapShape)
            .background(palette.pageBackground)
            .border(1.dp, statisticsPalette.mapBorder, mapShape),
    ) {
        countries.filterNot { it.code == "AQ" }.forEach { country ->
            country.rings.forEach { ring ->
                if (ring.size < 3) return@forEach

                val segments = mutableListOf<MutableList<com.mapmory.shared.presentation.map.domain.GeoPoint>>()
                ring.forEach { point ->
                    val current = segments.lastOrNull()
                    val previous = current?.lastOrNull()
                    if (previous != null && abs(point.longitude - previous.longitude) > 180f) {
                        segments.add(mutableListOf(point))
                    } else if (current == null) {
                        segments.add(mutableListOf(point))
                    } else {
                        current += point
                    }
                }

                segments.filter { it.size >= 3 }.forEach { segment ->
                    val path = Path().apply {
                        segment.forEachIndexed { index, point ->
                            val x = (point.longitude + 180f) / 360f * size.width
                            val y = (90f - point.latitude) / 180f * size.height
                            if (index == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    val visited = country.code in visitedCountryCodes
                    drawPath(path, color = if (visited) palette.primary else statisticsPalette.mapLand)
                    drawPath(
                        path,
                        color = if (visited) palette.secondaryAccent.copy(alpha = 0.75f) else statisticsPalette.mapOutline,
                        style = Stroke(width = 0.7.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun VisitProgressCard(statistics: TripStatisticsUiModel) {
    StatsCard {
        VisitProgress("전세계 방문률", statistics.worldVisitedCount, 195, "개국")
        Spacer(Modifier.height(21.dp))
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(TripStatisticsPalette.current.divider))
        Spacer(Modifier.height(18.dp))
        VisitProgress("대한민국 방문률", statistics.koreaVisitedCount, 17, "지역")
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(TripStatisticsPalette.current.divider))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 17.dp)) {
            SummaryValue("여행 기록", statistics.recordCount, Modifier.weight(1f))
            SummaryValue("사진", statistics.photoCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun VisitProgress(label: String, value: Int, total: Int, unit: String) {
    Text(label, color = TripRecordPalette.current.bodyText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.padding(top = 9.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(value.toString(), color = TripRecordPalette.current.headingText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(unit, color = TripRecordPalette.current.secondaryAccent, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
    }
    ProgressBar(progress = value.toFloat() / total)
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(TripRecordPalette.current.softSurface, CircleShape)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(10.dp)
                .background(TripRecordPalette.current.primary, CircleShape),
        )
    }
}

@Composable
private fun SummaryValue(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = TripRecordPalette.current.secondaryText, fontSize = 11.sp)
        Text(
            text = value.toString(),
            color = TripRecordPalette.current.headingText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RankingCard(topLocations: List<TopLocationUiModel>) {
    StatsCard {
        Text("가장 많이 방문한 곳", color = TripRecordPalette.current.headingText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (topLocations.isEmpty()) {
            Text(
                text = "아직 집계할 여행 기록이 없어요",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 17.dp, bottom = 4.dp),
            )
        } else {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                topLocations.take(3).forEachIndexed { index, location ->
                    RankingRow(index, location)
                }
            }
        }
    }
}

@Composable
private fun RankingRow(index: Int, location: TopLocationUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(
                color = if (index == 0) TripRecordPalette.current.primary else TripRecordPalette.current.softSurface,
                shape = CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                color = if (index == 0) TripRecordPalette.current.onPrimary else TripRecordPalette.current.secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = location.locationName,
            color = TripRecordPalette.current.headingText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text(
            text = "${location.visitCount}회",
            color = TripRecordPalette.current.secondaryAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun StatsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
    ) {
        Column(Modifier.padding(21.dp)) { content() }
    }
}

@Preview(
    name = "여행 통계",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripProfileScreenPreview() {
    PreviewSurface {
        TripProfileScreen(
            statisticsUiState = TripStatisticsUiState.Success(previewStatistics),
            onMapClick = {},
            onRecordClick = {},
            onCreateClick = {},
            onProfileClick = {},
        )
    }
}

@Preview(
    name = "여행 통계 로딩",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun LoadingTripProfileScreenPreview() {
    PreviewSurface {
        TripProfileScreen(
            statisticsUiState = TripStatisticsUiState.Loading,
            onMapClick = {},
            onRecordClick = {},
            onCreateClick = {},
            onProfileClick = {},
        )
    }
}

@Preview(
    name = "여행 통계 오류",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun ErrorTripProfileScreenPreview() {
    PreviewSurface {
        TripProfileScreen(
            statisticsUiState = TripStatisticsUiState.Error("여행 통계를 불러오지 못했어요."),
            onMapClick = {},
            onRecordClick = {},
            onCreateClick = {},
            onProfileClick = {},
        )
    }
}
