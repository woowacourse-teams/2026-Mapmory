package com.mapmory.shared.presentation.triprecord.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.ui.MapViewport
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
    TripRecordBackground(
        modifier = modifier,
        backgroundColor = TripRecordPalette.pageBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TripRecordPalette.pageBackground),
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
                // Keep zoomed map pixels inside a dedicated viewport so they cannot
                // paint over the bottom app navigation.
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
                        .background(TripRecordPalette.primary)
                        .clickable(onClick = onCreateClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "＋",
                        color = TripRecordPalette.onPrimary,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }

            TripBottomBar(
                modifier = Modifier.fillMaxWidth(),
                selected = TripBottomTab.MAP,
                onRecordClick = onRecordClick,
                onCreateClick = onCreateClick,
                onProfileClick = onProfileClick,
                backgroundColor = TripRecordPalette.pageBackground,
                dividerColor = TripRecordPalette.navigationDivider,
                selectedIconColor = TripRecordPalette.primary,
                selectedLabelColor = TripRecordPalette.navigationSelectedLabel,
                unselectedColor = TripRecordPalette.navigationUnselected,
                contentTopPadding = 6.dp,
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
                    color = TripMapPalette.logoText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "mory",
                    color = TripRecordPalette.secondaryAccent,
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
                color = TripMapPalette.scopeBackground,
                shape = RoundedCornerShape(12.dp),
            )
            .border(1.dp, TripMapPalette.scopeBorder, RoundedCornerShape(12.dp))
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
        color = if (selected) TripMapPalette.scopeSelectedText else TripMapPalette.scopeUnselectedText,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) TripMapPalette.scopeSelectedBackground else TripMapPalette.scopeBackground,
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
                color = if (selected) TripMapPalette.tagSelectedText else TripMapPalette.tagText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) TripRecordPalette.primary else TripMapPalette.tagBackground,
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
                color = TripRecordPalette.pageBackground,
                shape = cardShape,
            )
            .border(
                width = 1.dp,
                color = TripRecordPalette.border,
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
                color = TripRecordPalette.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(TripRecordPalette.accentSoft)
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
                    color = TripRecordPalette.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(safeVisitedCount.toString(), color = TripRecordPalette.primary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(" / $total", color = TripRecordPalette.headingText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = "$completionPercent% 채움",
                color = TripMapPalette.dashboardBadgeText,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(TripRecordPalette.primarySoft, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}
