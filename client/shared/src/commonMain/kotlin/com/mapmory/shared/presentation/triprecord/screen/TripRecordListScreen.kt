package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.presentation.triprecord.state.TripRecordFilterUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState

@Composable
fun TripRecordListScreen(
    uiState: TripRecordListUiState,
    filter: TripRecordFilterUiState,
    locations: List<Location>,
    onKeywordChanged: (String) -> Unit,
    onLocationChanged: (Long?) -> Unit,
    onSearchClick: () -> Unit,
    onPreviousPageClick: () -> Unit,
    onNextPageClick: () -> Unit,
    onCreateClick: () -> Unit,
    onMapClick: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TripRecordBackground(
        modifier = modifier.then(rememberDismissKeyboardOnTapModifier()),
        backgroundColor = TripRecordPalette.pageBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            JournalHeader(
                recordCount = (uiState as? TripRecordListUiState.Success)?.records?.size ?: 0,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                JournalTagFilters()
                Spacer(Modifier.height(18.dp))

                when (uiState) {
                    TripRecordListUiState.Idle,
                    TripRecordListUiState.Loading,
                    -> CircularProgressIndicator(
                        color = TripRecordPalette.accent,
                        modifier = Modifier.padding(top = 20.dp),
                    )

                    is TripRecordListUiState.Error -> Text(
                        text = uiState.message,
                        color = TripRecordPalette.danger,
                        modifier = Modifier.padding(top = 20.dp),
                    )

                    is TripRecordListUiState.Success -> {
                        if (uiState.records.isEmpty()) {
                            EmptyTripRecords(
                                hasFilter = filter.keyword.isNotBlank() || filter.locationId != null,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            TripRecordList(
                                records = uiState.records,
                                onRecordClick = onRecordClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (uiState.totalPages > 1) {
                            PageControls(
                                page = uiState.page,
                                totalPages = uiState.totalPages,
                                onPreviousPageClick = onPreviousPageClick,
                                onNextPageClick = onNextPageClick,
                            )
                        }
                    }
                }
            }

            TripBottomBar(
                selected = TripBottomTab.RECORD,
                onMapClick = onMapClick,
                onCreateClick = onCreateClick,
                onProfileClick = onProfileClick,
                backgroundColor = TripRecordPalette.pageBackground,
                dividerColor = TripRecordPalette.navigationDivider,
                selectedIconColor = TripRecordPalette.primary,
                selectedLabelColor = TripRecordPalette.navigationSelectedLabel,
                unselectedColor = TripRecordPalette.navigationUnselected,
            )
        }
    }
}

@Composable
private fun JournalHeader(recordCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TripRecordPalette.pageBackground)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "TRAVEL ARCHIVE",
                color = TripRecordPalette.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "모든 여행 기록",
                color = TripRecordPalette.headingText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(35.dp)
                .background(TripRecordPalette.primarySoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = recordCount.toString(),
                color = TripRecordPalette.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun JournalTagFilters() {
    val tags = remember { listOf("전체", "가족", "애인", "친구", "혼자") }
    var selectedTag by remember { mutableStateOf(tags.first()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            TripFilterChip(
                text = tag,
                selected = selectedTag == tag,
                onClick = { selectedTag = tag },
            )
        }
    }
}

@Composable
private fun TripFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) TripRecordPalette.primary else TripRecordPalette.secondaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) TripRecordPalette.primarySoft else TripRecordPalette.softSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun TripRecordList(
    records: List<TripRecordItemUiState>,
    onRecordClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
    ) {
        items(records, key = TripRecordItemUiState::id) { record ->
            TripRecordCard(
                record = record,
                onClick = { onRecordClick(record.id) },
            )
        }
    }
}

@Composable
private fun TripRecordCard(
    record: TripRecordItemUiState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.surface),
        border = BorderStroke(1.dp, TripRecordPalette.border),
    ) {
        Column {
            Box {
                TripPhotoImage(
                    imageBytes = record.photos.minByOrNull { it.sortOrder }?.previewBytes?.bytesForDecoding(),
                    contentDescription = record.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    placeholderVariant = record.id.toInt(),
                    shape = RoundedCornerShape(0.dp),
                )
                JournalImageBadge(
                    text = "⌖ ${record.locationName}",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 14.dp),
                )
                if (record.photos.size > 1) {
                    JournalImageBadge(
                        text = "${record.photos.size}장",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 14.dp, top = 14.dp),
                    )
                }
            }
            Column(Modifier.padding(18.dp)) {
                formattedDate(record)?.let { date ->
                    Text(
                        text = date,
                        color = TripRecordPalette.secondaryText,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    text = record.title,
                    color = TripRecordPalette.headingText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 7.dp),
                )
                Text(
                    text = record.content,
                    color = TripRecordPalette.bodyText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
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
        color = TripRecordPalette.contentOnMedia,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(TripRecordPalette.mediaScrim, RoundedCornerShape(10.dp))
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
            color = TripRecordPalette.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "새로운 여행의 순간을 기록해 보세요.",
            color = TripRecordPalette.muted,
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
            color = if (page > 0) TripRecordPalette.accent else TripRecordPalette.muted.copy(alpha = 0.45f),
            modifier = Modifier.clickable(enabled = page > 0, onClick = onPreviousPageClick),
        )
        Text(
            text = "${page + 1} / $totalPages",
            color = TripRecordPalette.contentOnMedia,
            fontSize = 12.sp,
        )
        Text(
            text = "다음 ›",
            color = if (page + 1 < totalPages) TripRecordPalette.accent else TripRecordPalette.muted.copy(alpha = 0.45f),
            modifier = Modifier.clickable(
                enabled = page + 1 < totalPages,
                onClick = onNextPageClick,
            ),
        )
    }
}
