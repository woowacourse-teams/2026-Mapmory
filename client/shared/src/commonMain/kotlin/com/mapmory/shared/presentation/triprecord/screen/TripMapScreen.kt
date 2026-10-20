package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import com.mapmory.shared.presentation.components.MapmoryBackButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    showRegionLabels: Boolean = false,
    onRegionLabelsToggle: () -> Unit = {},
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
        backgroundColor = TripRecordPalette.current.pageBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TripRecordPalette.current.pageBackground),
        ) {
            MapHeaderOverlay(
                mapScope = mapScope,
                visitedCount = visitedCount,
                mapDetailTotal = mapDetailTotal,
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

                TripRecordCreateButton(
                    source = "map_fab",
                    onClick = onCreateClick,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )

                Column(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mapScope == MapScope.KOREA && mapDetailTitle != null) {
                            MapmoryBackButton(
                                onClick = onMapDetailBackClick,
                                color = TripRecordPalette.current.headingText,
                                label = "$mapDetailTitle 지도에서 뒤로가기",
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        MapScopeToggle(selected = mapScope, onSelected = onMapScopeChange)
                    }
                    if (mapScope == MapScope.KOREA) {
                        TripTagChip(
                            text = if (showRegionLabels) "지역명 숨기기" else "지역명 표시",
                            selected = showRegionLabels,
                            onClick = onRegionLabelsToggle,
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
    mapDetailTotal: Int?,
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
        }
        Spacer(Modifier.height(14.dp))
        MapSummaryCard(
            mapScope = mapScope,
            visitedCount = visitedCount,
            mapDetailTotal = mapDetailTotal,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun MapScopeToggle(
    selected: MapScope,
    onSelected: (MapScope) -> Unit,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (selected == MapScope.KOREA) 0.dp else 59.dp,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "map-scope-thumb",
    )
    Box(
        modifier = Modifier
            .width(122.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(TripMapPalette.current.scopeBackground)
            .border(1.dp, TripMapPalette.current.scopeBorder, RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        Box(
            Modifier.offset(x = thumbOffset).width(59.dp).fillMaxHeight()
                .background(TripMapPalette.current.scopeSelectedBackground, RoundedCornerShape(6.dp)),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            MapScopeChip(
                label = "⌖ 대한민국",
                selected = selected == MapScope.KOREA,
                onClick = { if (selected != MapScope.KOREA) onSelected(MapScope.KOREA) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MapScopeChip(
                label = "◎ 전세계",
                selected = selected == MapScope.WORLD,
                onClick = { if (selected != MapScope.WORLD) onSelected(MapScope.WORLD) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MapScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) TripMapPalette.current.scopeSelectedText else TripMapPalette.current.scopeUnselectedText,
        animationSpec = tween(260),
        label = "map-scope-text",
    )
    Box(
        modifier = modifier.clip(RoundedCornerShape(6.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MapSummaryCard(
    mapScope: MapScope,
    visitedCount: Int,
    mapDetailTotal: Int?,
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
private fun MapSummaryCardPreview() {
    MapSummaryCard(
        mapScope = MapScope.WORLD,
        visitedCount = 3,
        mapDetailTotal = 9,
    )
}
