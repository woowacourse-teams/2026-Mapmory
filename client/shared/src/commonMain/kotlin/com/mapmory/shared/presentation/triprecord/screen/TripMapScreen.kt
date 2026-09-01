package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.ui.KoreaMapArtwork
import com.mapmory.shared.presentation.map.ui.MapViewport
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewVisitedRegions
import kotlin.math.roundToInt

@Composable
fun TripMapScreen(
    mapContent: @Composable () -> Unit,
    mapScope: MapScope = MapScope.WORLD,
    visitedCount: Int = 0,
    onMapScopeChange: (MapScope) -> Unit = {},
    onBackClick: () -> Unit,
    mapDetailTitle: String? = null,
    mapDetailTotal: Int? = null,
    onMapDetailBackClick: () -> Unit = {},
    onRecordClick: () -> Unit = onBackClick,
    onCreateClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    val analytics = LocalMapmoryAnalytics.current
    TripRecordBackground(
        modifier = modifier,
        backgroundColor = palette.pageBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TripRecordPalette.current.pageBackground),
        ) {
            MapHeaderOverlay(
                mapScope = mapScope,
                visitedCount = visitedCount,
                onMapScopeChange = onMapScopeChange,
                mapDetailTitle = mapDetailTitle,
                mapDetailTotal = mapDetailTotal,
                onMapDetailBackClick = onMapDetailBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                MapViewport(
                    modifier = Modifier.fillMaxSize(),
                    content = mapContent,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 28.dp, bottom = 33.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.primary)
                        .clickable {
                            analytics.logEvent(
                                MapmoryAnalyticsEvent.RECORD_CREATE_STARTED,
                                mapOf("source" to "map_fab"),
                            )
                            onCreateClick()
                        }
                        .semantics { contentDescription = "새 기록 작성" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(24.dp)) {
                        val strokeWidth = 2.dp.toPx()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val armLength = size.minDimension * 0.32f
                        drawLine(
                            color = palette.onPrimary,
                            start = Offset(center.x - armLength, center.y),
                            end = Offset(center.x + armLength, center.y),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = palette.onPrimary,
                            start = Offset(center.x, center.y - armLength),
                            end = Offset(center.x, center.y + armLength),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            TripBottomBar(
                modifier = Modifier.fillMaxWidth(),
                selected = TripBottomTab.MAP,
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
}

@Composable
private fun MapHeaderOverlay(
    mapScope: MapScope,
    visitedCount: Int,
    onMapScopeChange: (MapScope) -> Unit,
    mapDetailTitle: String?,
    mapDetailTotal: Int?,
    onMapDetailBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row {
                Text(
                    text = "Map",
                    color = TripMapPalette.current.logoText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "mory",
                    color = TripRecordPalette.current.secondaryAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            MapScopeToggle(
                selected = mapScope,
                onSelected = onMapScopeChange,
            )
        }
        Spacer(Modifier.height(14.dp))
        MapSummaryCard(
            mapScope = mapScope,
            visitedCount = visitedCount,
            mapDetailTitle = mapDetailTitle,
            mapDetailTotal = mapDetailTotal,
            onMapDetailBackClick = onMapDetailBackClick,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(14.dp))
        MapTagFilter(modifier = Modifier.padding(horizontal = 18.dp))
    }
}

@Composable
private fun MapScopeToggle(
    selected: MapScope,
    onSelected: (MapScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .width(174.dp)
            .background(
                color = TripMapPalette.current.scopeBackground,
                shape = RoundedCornerShape(12.dp),
            )
            .border(1.dp, TripMapPalette.current.scopeBorder, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        MapScopeChip(
            label = "⌖ 대한민국",
            selected = selected == MapScope.KOREA,
            onClick = { onSelected(MapScope.KOREA) },
            modifier = Modifier.weight(1f),
        )
        MapScopeChip(
            label = "◎ 전세계",
            selected = selected == MapScope.WORLD,
            onClick = { onSelected(MapScope.WORLD) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MapScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        color = if (selected) TripMapPalette.current.scopeSelectedText else TripMapPalette.current.scopeUnselectedText,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) TripMapPalette.current.scopeSelectedBackground else TripMapPalette.current.scopeBackground,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun MapTagFilter(modifier: Modifier = Modifier) {
    val tags = remember { listOf("전체", "가족", "애인", "친구", "혼자") }
    var selectedTag by remember { mutableStateOf(tags.first()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        tags.forEach { tag ->
            val selected = selectedTag == tag
            Text(
                text = tag,
                color = if (selected) TripMapPalette.current.tagSelectedText else TripMapPalette.current.tagText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) TripRecordPalette.current.primary else TripMapPalette.current.tagBackground,
                    )
                    .clickable { selectedTag = tag }
                    .padding(horizontal = 11.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MapSummaryCard(
    mapScope: MapScope,
    visitedCount: Int,
    mapDetailTitle: String?,
    mapDetailTotal: Int?,
    onMapDetailBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (mapScope) {
        MapScope.WORLD -> "나의 세계 지도"
        MapScope.KOREA -> "나의 대한민국 지도"
    }
    val total = mapDetailTotal ?: when (mapScope) {
        MapScope.WORLD -> 195
        MapScope.KOREA -> 17
    }
    val safeVisitedCount = visitedCount.coerceIn(0, total)
    val completionPercent = (safeVisitedCount.toFloat() / total * 100f).roundToInt()
    val cardShape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = TripRecordPalette.current.pageBackground,
                shape = cardShape,
            )
            .border(
                width = 1.dp,
                color = TripRecordPalette.current.border,
                shape = cardShape,
            )
            .padding(
                start = 18.dp,
                top = 15.dp,
                end = 18.dp,
                bottom = 14.dp,
            ),
    ) {
        mapDetailTitle?.let { title ->
            Text(
                text = "← $title 전체에서 나가기",
                color = TripRecordPalette.current.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TripRecordPalette.current.accentSoft)
                    .clickable(onClick = onMapDetailBackClick)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = title,
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        safeVisitedCount.toString(),
                        color = TripRecordPalette.current.primary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        " / $total",
                        color = TripRecordPalette.current.headingText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "$completionPercent% 채움",
                color = TripMapPalette.current.dashboardBadgeText,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(TripRecordPalette.current.primarySoft, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Preview(
    name = "여행 지도 화면",
    showBackground = true,
    widthDp = 412,
    heightDp = 760,
)
@Composable
private fun TripMapScreenPreview() {
    PreviewSurface {
        TripMapScreen(
            mapContent = { TripMapArtwork() },
            mapScope = MapScope.KOREA,
            visitedCount = 8,
            onBackClick = {},
        )
    }
}

@Preview(
    name = "여행 지도 상세 화면",
    showBackground = true,
    widthDp = 412,
    heightDp = 760,
)
@Composable
private fun TripMapDetailScreenPreview() {
    PreviewSurface {
        TripMapScreen(
            mapContent = {
                KoreaMapArtwork(
                    regions = GeneratedKoreaMapData.provinces,
                    visitedRegionCodes = previewVisitedRegions,
                    showRegionLabels = true,
                )
            },
            mapScope = MapScope.KOREA,
            visitedCount = 3,
            mapDetailTitle = "서울특별시",
            mapDetailTotal = 25,
            onBackClick = {},
        )
    }
}

@Preview
@Composable
private fun MapScopeChipPreview() {
    MapScopeChip(
        label = "대한민국",
        selected = true,
        onClick = {},
    )
}

@Preview
@Composable
private fun MapTagFilterPreview() {
    MapTagFilter()
}

@Preview
@Composable
private fun MapSummaryCardPreview() {
    MapSummaryCard(
        mapScope = MapScope.WORLD,
        visitedCount = 3,
        mapDetailTitle = "끄룽텝 마하나콘 아몬 랏따나꼬신 마힌따라 아유타야 마하딜록 뽑놉빠랏 랏차타니 부리롬 우돔랏차니우엣 마하싸탄 아몬삐만 아와딴싸티 싸카타띠야 위쓰누깜쁘라씻",
        // 방콕의 본 도시명에 세계에서 제일 긴 도시 명이랍니다...
        mapDetailTotal = 9,
        onMapDetailBackClick = {},
    )
}
