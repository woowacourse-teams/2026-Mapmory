package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.domain.model.PlaceAttribution
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.ui.KoreaMapArtwork
import com.mapmory.shared.presentation.map.ui.MapViewport
import com.mapmory.shared.presentation.map.viewmodel.MapPlaceSearchState
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewVisitedRegions
import kotlin.math.roundToInt

@Composable
fun TripMapScreen(
    mapContent: @Composable () -> Unit,
    mapScope: MapScope = MapScope.WORLD,
    visitedCount: Int = 0,
    onMapScopeChange: (MapScope) -> Unit = {},
    placeSearch: MapPlaceSearchState = MapPlaceSearchState(),
    onPlaceSearchQueryChange: (String) -> Unit = {},
    onPlaceSearchSubmit: () -> Unit = {},
    onPlaceSearchResultClick: (PlaceCandidate) -> Unit = {},
    showRegionLabels: Boolean = false,
    onRegionLabelsToggle: () -> Unit = {},
    isCurrentLocationLoading: Boolean = false,
    currentLocationError: String? = null,
    onCurrentLocationClick: () -> Unit = {},
    onBackClick: () -> Unit,
    mapDetailTitle: String? = null,
    mapDetailTotal: Int? = null,
    onMapDetailBackClick: () -> Unit = {},
    onRecordClick: () -> Unit = onBackClick,
    onCreateClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
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
                mapDetailTitle = mapDetailTitle,
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
                val mapStateHolder = rememberSaveableStateHolder()
                MapViewport(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    mapStateHolder.SaveableStateProvider(mapScope.name, mapContent)
                }

                TripRecordCreateButton(
                    source = "map_fab",
                    onClick = onCreateClick,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )

                CurrentLocationMapButton(
                    isLoading = isCurrentLocationLoading,
                    onClick = onCurrentLocationClick,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )

                currentLocationError?.let { message ->
                    Text(
                        text = message,
                        color = TripRecordPalette.current.headingText,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 146.dp)
                            .widthIn(max = 320.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TripRecordPalette.current.surface)
                            .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mapScope == MapScope.KOREA && mapDetailTitle != null) {
                            MapmoryBackButton(
                                onClick = onMapDetailBackClick,
                                color = TripRecordPalette.current.headingText,
                                label = "$mapDetailTitle 지도에서 뒤로가기",
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        MapPlaceSearchField(
                            state = placeSearch,
                            onQueryChange = onPlaceSearchQueryChange,
                            onSubmit = onPlaceSearchSubmit,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        MapScopeToggle(selected = mapScope, onSelected = onMapScopeChange)
                    }
                    MapPlaceSearchResults(
                        state = placeSearch,
                        onResultClick = onPlaceSearchResultClick,
                    )
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
                onSettingsClick = onSettingsClick,
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
private fun MapPlaceSearchField(
    state: MapPlaceSearchState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(17.dp)) {
            val center = Offset(size.width * 0.42f, size.height * 0.42f)
            val radius = size.minDimension * 0.27f
            val stroke = 1.8.dp.toPx()
            drawCircle(palette.secondaryText, radius, center, style = Stroke(stroke))
            drawLine(
                palette.secondaryText,
                Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
                Offset(size.width * 0.9f, size.height * 0.9f),
                stroke,
                cap = StrokeCap.Round,
            )
        }
        Box(
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (state.query.isBlank()) {
                Text(
                    text = "장소 검색",
                    color = palette.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = palette.headingText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "장소 검색" },
            )
        }
        when {
            state.isSearching || state.isSelecting -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = palette.primary,
                strokeWidth = 2.dp,
            )
            state.query.isNotBlank() -> Text(
                text = "×",
                color = palette.secondaryText,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") }
                    .semantics { contentDescription = "검색어 지우기" },
            )
        }
    }
}

@Composable
private fun MapPlaceSearchResults(
    state: MapPlaceSearchState,
    onResultClick: (PlaceCandidate) -> Unit,
) {
    if (state.query.isBlank() ||
        (!state.isSearching && !state.isSelecting && state.results.isEmpty() &&
            !state.hasSearched && state.errorMessage == null)
    ) return

    val palette = TripRecordPalette.current
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        when {
            state.isSearching || state.isSelecting -> Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = palette.primary, strokeWidth = 2.dp)
                Text(
                    text = if (state.isSelecting) "지역 정보를 확인하고 있어요." else "장소를 찾고 있어요.",
                    color = palette.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 9.dp),
                )
            }
            state.errorMessage != null -> Text(
                text = state.errorMessage,
                color = palette.danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            state.results.isEmpty() && state.hasSearched -> Text(
                text = "장소 검색 결과가 없어요.",
                color = palette.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            else -> {
                Column(
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                ) {
                    state.results.forEachIndexed { index, candidate ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .clickable(
                                    enabled = !state.isSelecting,
                                    role = Role.Button,
                                    onClick = { onResultClick(candidate) },
                                )
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = candidate.name,
                                color = palette.headingText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            candidate.address?.let { address ->
                                Text(
                                    text = address,
                                    color = palette.secondaryText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (index < state.results.lastIndex) {
                            Spacer(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                        }
                    }
                }
                state.results.firstOrNull()?.let { candidate ->
                    val attribution = candidate.attribution ?: "© OpenStreetMap contributors"
                    val isGoogle = PlaceAttribution.isGoogleMaps(candidate.attribution, candidate.attributionUrl)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 5.dp)
                            .then(
                                if (candidate.attributionUrl.isNullOrBlank()) Modifier
                                else Modifier.clickable { runCatching { uriHandler.openUri(candidate.attributionUrl) } },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isGoogle) PlaceAttribution.GoogleMaps else attribution,
                            color = if (isGoogle && LocalMapmoryTheme.current.isDark) Color.White
                            else if (isGoogle) Color(0xFF5E5E5E) else palette.secondaryText,
                            fontSize = if (isGoogle) 12.sp else 10.sp,
                            maxLines = 1,
                        )
                        if (!isGoogle) {
                            Text(
                                text = "Powered by Geoapify",
                                color = palette.primary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentLocationMapButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    val iconColor = palette.bodyText
    Box(
        modifier = modifier
            .padding(end = 28.dp, bottom = 89.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = !isLoading, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (isLoading) "현재 위치 확인 중" else "현재 위치 표시"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .border(1.dp, palette.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = iconColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Canvas(Modifier.size(22.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension * 0.22f
                    val strokeWidth = 1.8.dp.toPx()
                    val gap = radius + 1.dp.toPx()
                    drawCircle(iconColor, radius, center, style = Stroke(strokeWidth))
                    drawLine(iconColor, Offset(center.x, 0f), Offset(center.x, center.y - gap), strokeWidth, cap = StrokeCap.Round)
                    drawLine(iconColor, Offset(center.x, center.y + gap), Offset(center.x, size.height), strokeWidth, cap = StrokeCap.Round)
                    drawLine(iconColor, Offset(0f, center.y), Offset(center.x - gap, center.y), strokeWidth, cap = StrokeCap.Round)
                    drawLine(iconColor, Offset(center.x + gap, center.y), Offset(size.width, center.y), strokeWidth, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun MapHeaderOverlay(
    mapScope: MapScope,
    visitedCount: Int,
    mapDetailTitle: String?,
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
            mapDetailTitle = mapDetailTitle,
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
    mapDetailTitle: String?,
    mapDetailTotal: Int?,
    modifier: Modifier = Modifier,
) {
    val title = when (mapScope) {
        MapScope.WORLD -> "나의 세계 지도"
        MapScope.KOREA -> "나의 ${mapDetailTitle ?: "대한민국"} 지도"
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
        mapDetailTitle = null,
        mapDetailTotal = 9,
    )
}
