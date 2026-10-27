package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.lazy.rememberLazyListState
import com.mapmory.shared.presentation.components.ScrollToTopEffect

import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.app.BackgroundSaveStatus
import com.mapmory.shared.app.BackgroundTripRecordSave
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.presentation.triprecord.state.TripRecordFilterUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewUiRecords

@Composable
fun TripRecordListScreen(
    uiState: TripRecordListUiState,
    filter: TripRecordFilterUiState,
    pendingSaves: List<BackgroundTripRecordSave> = emptyList(),
    onRetryPendingSave: (Long) -> Unit = {},
    onDismissPendingSave: (Long) -> Unit = {},
    onPreviousPageClick: () -> Unit,
    onNextPageClick: () -> Unit,
    onCreateClick: () -> Unit,
    onMapClick: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onRetryClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isTimelineLayout: Boolean = false,
    showLayoutSwitcher: Boolean = false,
    onTimelineLayoutChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val analytics = LocalMapmoryAnalytics.current
    val hasScrollableRecordList = (uiState as? TripRecordListUiState.Success)
        ?.let { it.records.isNotEmpty() || pendingSaves.isNotEmpty() } == true
    TripRecordBackground(
        modifier = modifier.then(rememberDismissKeyboardOnTapModifier()),
        backgroundColor = TripRecordPalette.current.pageBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            JournalHeader(
                recordCount = ((uiState as? TripRecordListUiState.Success)?.records?.size ?: 0) +
                    pendingSaves.size,
                isTimelineLayout = isTimelineLayout,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                ) {
                    if (showLayoutSwitcher && !hasScrollableRecordList) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            JournalLayoutSwitcher(
                                isTimelineLayout = isTimelineLayout,
                                onTimelineLayoutChange = onTimelineLayoutChange,
                            )
                        }
                    }
                    if (!hasScrollableRecordList) Spacer(Modifier.height(14.dp))

                    when (uiState) {
                        TripRecordListUiState.Idle,
                        TripRecordListUiState.Loading,
                        -> if (pendingSaves.isEmpty()) {
                            TripRecordListSkeleton(modifier = Modifier.weight(1f))
                        } else {
                            TripRecordList(
                                records = emptyList(),
                                pendingSaves = pendingSaves,
                                onRetryPendingSave = onRetryPendingSave,
                                onDismissPendingSave = onDismissPendingSave,
                                onRecordClick = {},
                                modifier = Modifier.weight(1f),
                            )
                        }

                        is TripRecordListUiState.Error -> if (pendingSaves.isEmpty()) {
                            TripRecordLoadError(
                                message = uiState.message,
                                onRetryClick = onRetryClick,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            TripRecordList(
                                records = emptyList(),
                                pendingSaves = pendingSaves,
                                onRetryPendingSave = onRetryPendingSave,
                                onDismissPendingSave = onDismissPendingSave,
                                onRecordClick = {},
                                modifier = Modifier.weight(1f),
                            )
                        }

                        is TripRecordListUiState.Success -> {
                            if (!uiState.isRefreshing && uiState.refreshError != null) {
                                TripRecordRefreshError(
                                    onRetryClick = onRetryClick,
                                )
                            }
                            if (uiState.records.isEmpty() && pendingSaves.isEmpty()) {
                                EmptyTripRecords(
                                    hasFilter = filter.locationId != null || filter.selectedTagId != null,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                val openRecord: (Long) -> Unit = { recordId ->
                                    analytics.logEvent(
                                        MapmoryAnalyticsEvent.JOURNAL_RECORD_OPENED,
                                        mapOf("record_id" to recordId.toString(), "source" to "journal"),
                                    )
                                    onRecordClick(recordId)
                                }
                                if (isTimelineLayout) {
                                    TripRecordTimelineList(
                                        records = uiState.records,
                                        pendingSaves = pendingSaves,
                                        onRetryPendingSave = onRetryPendingSave,
                                        onDismissPendingSave = onDismissPendingSave,
                                        onRecordClick = openRecord,
                                        showLayoutSwitcher = showLayoutSwitcher && hasScrollableRecordList,
                                        isTimelineLayout = isTimelineLayout,
                                        onTimelineLayoutChange = onTimelineLayoutChange,
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    TripRecordPhotoList(
                                        records = uiState.records,
                                        pendingSaves = pendingSaves,
                                        onRetryPendingSave = onRetryPendingSave,
                                        onDismissPendingSave = onDismissPendingSave,
                                        onRecordClick = openRecord,
                                        showLayoutSwitcher = showLayoutSwitcher && hasScrollableRecordList,
                                        isTimelineLayout = isTimelineLayout,
                                        onTimelineLayoutChange = onTimelineLayoutChange,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            if (uiState.totalPages > 1) {
                                PageControls(
                                    page = uiState.page,
                                    totalPages = uiState.totalPages,
                                    onPreviousPageClick = {
                                        analytics.logEvent(
                                            MapmoryAnalyticsEvent.JOURNAL_PAGE_CHANGED,
                                            mapOf("direction" to "previous"),
                                        )
                                        onPreviousPageClick()
                                    },
                                    onNextPageClick = {
                                        analytics.logEvent(
                                            MapmoryAnalyticsEvent.JOURNAL_PAGE_CHANGED,
                                            mapOf("direction" to "next"),
                                        )
                                        onNextPageClick()
                                    },
                                )
                            }
                        }
                    }
                }

                TripRecordCreateButton(
                    source = "journal_fab",
                    onClick = onCreateClick,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }

            TripBottomBar(
                selected = TripBottomTab.RECORD,
                onMapClick = onMapClick,
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
internal fun JournalLayoutSwitcher(
    isTimelineLayout: Boolean,
    onTimelineLayoutChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(TripRecordPalette.current.surfaceElevated)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(false to "A 사진", true to "B 시간").forEach { (timeline, label) ->
            val selected = isTimelineLayout == timeline
            Text(
                text = label,
                color = if (selected) TripRecordPalette.current.primary else TripRecordPalette.current.secondaryText,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) TripRecordPalette.current.surface else TripRecordPalette.current.surfaceElevated)
                    .clickable(role = Role.Button) { onTimelineLayoutChange(timeline) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun TripRecordRefreshError(
    onRetryClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "새로 고치지 못했어요. 이전 목록을 보여드려요.",
            color = TripRecordPalette.current.bodyText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetryClick) {
            Text("다시 시도", color = TripRecordPalette.current.primary)
        }
    }
}

@Composable
private fun TripRecordLoadError(
    message: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
            border = BorderStroke(1.dp, TripRecordPalette.current.border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "여행 기록을 불러오지 못했어요.",
                    color = TripRecordPalette.current.headingText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message.ifBlank { "인터넷 연결을 확인한 뒤 다시 시도해 주세요." },
                    color = TripRecordPalette.current.bodyText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
                Button(
                    onClick = onRetryClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TripRecordPalette.current.primary,
                        contentColor = TripRecordPalette.current.onPrimary,
                    ),
                ) {
                    Text("다시 시도")
                }
            }
        }
    }
}

@Composable
private fun JournalHeader(recordCount: Int, isTimelineLayout: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TripRecordPalette.current.pageBackground)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "MY TRAVEL JOURNAL",
                color = TripRecordPalette.current.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "나의 여행",
                color = TripRecordPalette.current.headingText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = when {
                    isTimelineLayout -> "언제, 어디서 남긴 기록인지 차례로"
                    else -> "사진을 따라 그날의 기억으로"
                },
                color = TripRecordPalette.current.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = "${recordCount}개",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TripRecordList(
    records: List<TripRecordItemUiState>,
    pendingSaves: List<BackgroundTripRecordSave>,
    onRetryPendingSave: (Long) -> Unit,
    onDismissPendingSave: (Long) -> Unit,
    onRecordClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    ScrollToTopEffect(listState)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
    ) {
        items(pendingSaves, key = { save -> "pending-${save.id}" }) { save ->
            PendingTripRecordCard(
                save = save,
                onRetry = { onRetryPendingSave(save.id) },
                onDismiss = { onDismissPendingSave(save.id) },
            )
        }
        items(records, key = TripRecordItemUiState::id) { record ->
            TripRecordCard(
                record = record,
                onClick = { onRecordClick(record.id) },
            )
        }
    }
}

@Composable
private fun TripRecordPhotoList(
    records: List<TripRecordItemUiState>,
    pendingSaves: List<BackgroundTripRecordSave>,
    onRetryPendingSave: (Long) -> Unit,
    onDismissPendingSave: (Long) -> Unit,
    onRecordClick: (Long) -> Unit,
    showLayoutSwitcher: Boolean,
    isTimelineLayout: Boolean,
    onTimelineLayoutChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    ScrollToTopEffect(listState)
    val featuredRecord = records.firstOrNull()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = if (showLayoutSwitcher) 0.dp else 14.dp,
            bottom = 18.dp,
        ),
    ) {
        val switcherWithFeaturedRecord = showLayoutSwitcher && pendingSaves.isEmpty() && featuredRecord != null
        if (showLayoutSwitcher && !switcherWithFeaturedRecord) {
            item(key = "journal-layout-switcher") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    JournalLayoutSwitcher(
                        isTimelineLayout = isTimelineLayout,
                        onTimelineLayoutChange = onTimelineLayoutChange,
                    )
                }
            }
        }
        items(pendingSaves, key = { save -> "pending-${save.id}" }) { save ->
            PendingTripRecordCard(
                save = save,
                onRetry = { onRetryPendingSave(save.id) },
                onDismiss = { onDismissPendingSave(save.id) },
            )
        }
        if (featuredRecord != null) {
            item(key = "featured-${featuredRecord.id}") {
                Column {
                    if (switcherWithFeaturedRecord) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            JournalLayoutSwitcher(
                                isTimelineLayout = isTimelineLayout,
                                onTimelineLayoutChange = onTimelineLayoutChange,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    FeaturedTripRecordCard(
                        record = featuredRecord,
                        onClick = { onRecordClick(featuredRecord.id) },
                    )
                }
            }
            if (records.size > 1) {
                item(key = "recent-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "최근에 남긴 기록",
                            color = TripRecordPalette.current.headingText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${records.size - 1}개",
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 11.sp,
                        )
                    }
                }
                items(records.drop(1).chunked(2), key = { row -> "recent-${row.first().id}" }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { record ->
                            CompactPhotoTripRecordCard(
                                record = record,
                                onClick = { onRecordClick(record.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedTripRecordCard(
    record: TripRecordItemUiState,
    onClick: () -> Unit,
) {
    val photo = record.photos.minByOrNull { it.sortOrder }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(258.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        if (photo != null) {
            MapmoryAsyncImage(
                imageBytes = photo.previewBytes?.bytesForDecoding(),
                imageUri = photo.previewUri,
                fallbackUri = photo.fullResolutionUri,
                contentDescription = "${record.locationName} ${record.title} 대표 사진",
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(20.dp),
            )
        } else {
            Box(Modifier.fillMaxSize().background(TripRecordPalette.current.primarySoft))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "${record.locationName} · ${recordDateRange(record)}",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (record.title.isNotBlank()) {
                Text(
                    record.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "사진 ${record.photoCount ?: record.photos.size}장  ›",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CompactPhotoTripRecordCard(
    record: TripRecordItemUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photo = record.photos.minByOrNull { it.sortOrder }
    Card(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(96.dp).background(TripRecordPalette.current.imagePlaceholder),
            ) {
                if (photo != null) {
                    MapmoryAsyncImage(
                        imageBytes = photo.previewBytes?.bytesForDecoding(),
                        imageUri = photo.previewUri,
                        fallbackUri = photo.fullResolutionUri,
                        contentDescription = "${record.locationName} 여행 사진",
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(0.dp),
                    )
                }
                Text(
                    "사진 ${record.photoCount ?: record.photos.size}장",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .background(TripRecordPalette.current.mediaScrim, RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (record.title.isNotBlank()) {
                    Text(
                        record.title,
                        color = TripRecordPalette.current.headingText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${record.locationName} · ${record.endDate ?: record.startDate ?: "날짜 미정"}",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TripRecordTimelineList(
    records: List<TripRecordItemUiState>,
    pendingSaves: List<BackgroundTripRecordSave>,
    onRetryPendingSave: (Long) -> Unit,
    onDismissPendingSave: (Long) -> Unit,
    onRecordClick: (Long) -> Unit,
    showLayoutSwitcher: Boolean,
    isTimelineLayout: Boolean,
    onTimelineLayoutChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    ScrollToTopEffect(listState)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = if (showLayoutSwitcher) 0.dp else 14.dp,
            bottom = 18.dp,
        ),
    ) {
        if (showLayoutSwitcher) {
            item(key = "journal-layout-switcher") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    JournalLayoutSwitcher(
                        isTimelineLayout = isTimelineLayout,
                        onTimelineLayoutChange = onTimelineLayoutChange,
                    )
                }
            }
        }
        items(pendingSaves, key = { save -> "pending-${save.id}" }) { save ->
            PendingTripRecordCard(
                save = save,
                onRetry = { onRetryPendingSave(save.id) },
                onDismiss = { onDismissPendingSave(save.id) },
            )
        }
        records.groupBy(::recordMonthLabel).forEach { (month, monthRecords) ->
            item(key = "month-$month") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).background(TripRecordPalette.current.primary, CircleShape))
                    Text(
                        text = month,
                        color = TripRecordPalette.current.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(TripRecordPalette.current.border),
                    )
                }
            }
            items(monthRecords, key = { record -> "record-${record.id}" }) { record ->
                TimelineTripRecordCard(record, onClick = { onRecordClick(record.id) })
            }
        }
    }
}

@Composable
private fun TimelineTripRecordCard(
    record: TripRecordItemUiState,
    onClick: () -> Unit,
) {
    val photo = record.photos.minByOrNull { it.sortOrder }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 94.dp, height = 83.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(TripRecordPalette.current.imagePlaceholder),
            ) {
                if (photo != null) {
                    MapmoryAsyncImage(
                        imageBytes = photo.previewBytes?.bytesForDecoding(),
                        imageUri = photo.previewUri,
                        fallbackUri = photo.fullResolutionUri,
                        contentDescription = "${record.locationName} 대표 사진",
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(9.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    recordDateRange(record),
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.title,
                    color = TripRecordPalette.current.headingText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${record.locationName} · 사진 ${record.photoCount ?: record.photos.size}장${if (record.content.isNotBlank()) " · 메모" else ""}",
                    color = TripRecordPalette.current.bodyText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", color = TripRecordPalette.current.secondaryText, fontSize = 22.sp)
        }
    }
}

private fun recordMonthLabel(record: TripRecordItemUiState): String {
    val date = record.endDate?.takeIf(String::isNotBlank)
        ?: record.startDate?.takeIf(String::isNotBlank)
        ?: return "날짜 미정"
    val parts = date.split('-')
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return "날짜 미정"
    return "${parts.first()}년 ${month}월"
}

internal fun recordDateRange(record: TripRecordItemUiState): String {
    val start = record.startDate?.replace('-', '.')
    val end = record.endDate?.replace('-', '.')
    return when {
        start != null && end != null && start != end -> "$start – $end"
        start != null -> start
        end != null -> end
        else -> "날짜 미정"
    }
}

@Composable
private fun PendingTripRecordCard(
    save: BackgroundTripRecordSave,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val failed = save.status == BackgroundSaveStatus.FAILED
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(TripRecordPalette.current.primarySoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${save.photoCount}장",
                    color = TripRecordPalette.current.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${save.startDate.replace("-", ". ")} ${if (failed) "저장 실패" else "저장 중"}",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 11.sp,
                )
                Text(
                    text = save.locationName,
                    color = TripRecordPalette.current.headingText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
                Text(
                    text = if (failed) {
                        save.errorMessage ?: "기록 저장에 실패했어요."
                    } else {
                        save.title
                    },
                    color = if (failed) TripRecordPalette.current.danger else TripRecordPalette.current.bodyText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
                if (!failed) {
                    Text(
                        text = when {
                            save.status == BackgroundSaveStatus.QUEUED -> "업로드 대기 중"
                            save.progressPercent >= 95 -> "기록 저장 중"
                            save.remainingSeconds != null -> "약 ${save.remainingSeconds}초 남음"
                            else -> "남은 시간 계산 중"
                        },
                        color = TripRecordPalette.current.secondaryText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
                if (failed) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "다시 시도",
                            color = TripRecordPalette.current.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onRetry),
                        )
                        Text(
                            text = "삭제",
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable(onClick = onDismiss),
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun TripRecordCard(
    record: TripRecordItemUiState,
    onClick: () -> Unit,
) {
    TripRecordCardLayout(
        modifier = Modifier.clickable(onClick = onClick),
        photo = {
            MapmoryAsyncImage(
                imageBytes = record.photos.minByOrNull { it.sortOrder }?.previewBytes?.bytesForDecoding(),
                imageUri = record.photos.minByOrNull { it.sortOrder }?.previewUri,
                contentDescription = record.locationName,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
            )
        },
        details = {
            val latestDate = record.endDate?.takeIf { it.isNotBlank() } ?: record.startDate
            latestDate?.takeIf { it.isNotBlank() }?.let { date ->
                Text(
                    text = "${date.replace("-", ". ")} 최근 방문",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = record.locationName,
                color = TripRecordPalette.current.headingText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 7.dp),
            )
            if (record.content.isNotBlank()) {
                Text(
                    text = record.content,
                    color = TripRecordPalette.current.bodyText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        },
    )
}

/** 실제 목록과 스켈레톤이 같은 카드 크기와 정렬을 사용한다. */
@Composable
internal fun TripRecordCardLayout(
    photo: @Composable () -> Unit,
    details: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                photo()
            }
            Column(Modifier.weight(1f)) { details() }
            Text("›", color = TripRecordPalette.current.muted, fontSize = 25.sp)
        }
    }
}

@Composable
private fun JournalImageBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = TripRecordPalette.current.contentOnMedia,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(TripRecordPalette.current.mediaScrim, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

private fun formattedDate(record: TripRecordItemUiState): String? {
    val start = record.startDate?.replace('-', '.')
    val end = record.endDate?.replace('-', '.')
    return when {
        start != null && end != null && start != end -> "$start – $end"
        start != null -> start
        end != null -> end
        else -> null
    }
}

@Composable
private fun EmptyTripRecords(
    hasFilter: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasFilter) "조건에 맞는 여행 기록이 없어요." else "아직 작성한 여행 기록이 없어요.",
            color = TripRecordPalette.current.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "새로운 여행의 순간을 기록해 보세요.",
            color = TripRecordPalette.current.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PageControls(
    page: Int,
    totalPages: Int,
    onPreviousPageClick: () -> Unit,
    onNextPageClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "‹ 이전",
            color = if (page > 0) TripRecordPalette.current.accent else TripRecordPalette.current.muted.copy(alpha = 0.45f),
            modifier = Modifier.clickable(enabled = page > 0, onClick = onPreviousPageClick),
        )
        Text(
            text = "${page + 1} / $totalPages",
            color = TripRecordPalette.current.contentOnMedia,
            fontSize = 12.sp,
        )
        Text(
            text = "다음 ›",
            color = if (page + 1 < totalPages) TripRecordPalette.current.accent else TripRecordPalette.current.muted.copy(alpha = 0.45f),
            modifier = Modifier.clickable(
                enabled = page + 1 < totalPages,
                onClick = onNextPageClick,
            ),
        )
    }
}

@Preview(
    name = "여행 기록 목록",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordListScreenPreview() {
    PreviewSurface {
        TripRecordListScreen(
            uiState = TripRecordListUiState.Success(previewUiRecords, page = 0, totalPages = 3),
            filter = TripRecordFilterUiState(),
            onPreviousPageClick = {},
            onNextPageClick = {},
            onCreateClick = {},
            onMapClick = {},
            onRecordClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 없음",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun EmptyTripRecordListScreenPreview() {
    PreviewSurface {
        TripRecordListScreen(
            uiState = TripRecordListUiState.Success(emptyList(), page = 0, totalPages = 0),
            filter = TripRecordFilterUiState(),
            onPreviousPageClick = {},
            onNextPageClick = {},
            onCreateClick = {},
            onMapClick = {},
            onRecordClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 목록 로딩",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun LoadingTripRecordListScreenPreview() {
    PreviewSurface {
        TripRecordListScreen(
            uiState = TripRecordListUiState.Loading,
            filter = TripRecordFilterUiState(),
            onPreviousPageClick = {},
            onNextPageClick = {},
            onCreateClick = {},
            onMapClick = {},
            onRecordClick = {},
            onRetryClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 목록 오류",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun ErrorTripRecordListScreenPreview() {
    PreviewSurface {
        TripRecordListScreen(
            uiState = TripRecordListUiState.Error("여행 기록을 불러오지 못했어요."),
            filter = TripRecordFilterUiState(),
            onPreviousPageClick = {},
            onNextPageClick = {},
            onCreateClick = {},
            onMapClick = {},
            onRecordClick = {},
            onRetryClick = {},
        )
    }
}
