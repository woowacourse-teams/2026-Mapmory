package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
    onRecordClick: () -> Unit = onBackClick,
    onCreateClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TripRecordBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
            ) {
                TripRecordTopBar(
                    title = "Mapmory",
                    trailing = {
                        Text(
                            text = "나의 여행으로 채우는 지도",
                            color = TripRecordPalette.muted,
                            fontSize = 10.sp,
                        )
                    },
                )
                Spacer(Modifier.height(4.dp))
                MapSummaryCard(mapScope = mapScope, visitedCount = visitedCount)
                Spacer(Modifier.height(10.dp))
                MapScopeToggle(
                    selected = mapScope,
                    onSelected = onMapScopeChange,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // Keep zoomed map pixels inside a dedicated viewport so they cannot
                // paint over the header or the bottom app navigation.
                MapViewport(
                    modifier = Modifier.fillMaxSize(),
                    content = mapContent,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp)
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(TripRecordPalette.accent)
                        .clickable(onClick = onCreateClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "＋",
                        color = TripRecordPalette.background,
                        fontSize = 39.sp,
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
            )
        }
    }
}

@Composable
private fun MapScopeToggle(
    selected: MapScope,
    onSelected: (MapScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = TripRecordPalette.background.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(50),
                )
                .padding(4.dp),
        ) {
            MapScopeChip(
                label = "대한민국",
                selected = selected == MapScope.KOREA,
                onClick = { onSelected(MapScope.KOREA) },
            )
            MapScopeChip(
                label = "전세계",
                selected = selected == MapScope.WORLD,
                onClick = { onSelected(MapScope.WORLD) },
            )
        }
    }
}

@Composable
private fun MapScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) TripRecordPalette.background else TripRecordPalette.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) TripRecordPalette.accent else TripRecordPalette.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

@Composable
private fun MapSummaryCard(
    mapScope: MapScope,
    visitedCount: Int,
) {
    val title = when (mapScope) {
        MapScope.WORLD -> "나의 세계 지도"
        MapScope.KOREA -> "나의 대한민국 지도"
    }
    val total = when (mapScope) {
        MapScope.WORLD -> 195
        MapScope.KOREA -> 17
    }
    val safeVisitedCount = visitedCount.coerceIn(0, total)
    val completionPercent = (safeVisitedCount.toFloat() / total * 100f).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = TripRecordPalette.background.copy(alpha = 0.96f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, color = TripRecordPalette.muted, fontSize = 11.sp)
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(safeVisitedCount.toString(), color = TripRecordPalette.accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(" / $total", color = TripRecordPalette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = "$completionPercent% 채움",
                color = TripRecordPalette.accent,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(TripRecordPalette.accentSoft, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}
