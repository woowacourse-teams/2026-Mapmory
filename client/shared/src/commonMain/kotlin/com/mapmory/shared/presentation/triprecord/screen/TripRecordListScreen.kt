package com.mapmory.shared.presentation.triprecord.screen

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    modifier: Modifier = Modifier,
) {
    val analytics = LocalMapmoryAnalytics.current
    TripRecordBackground(
        modifier = modifier.then(rememberDismissKeyboardOnTapModifier()),
        backgroundColor = TripRecordPalette.current.pageBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            JournalHeader(
                recordCount = ((uiState as? TripRecordListUiState.Success)?.records?.size ?: 0) +
                    pendingSaves.size,
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
                    Spacer(Modifier.height(14.dp))

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
                                TripRecordList(
                                    records = uiState.records,
                                    pendingSaves = pendingSaves,
                                    onRetryPendingSave = onRetryPendingSave,
                                    onDismissPendingSave = onDismissPendingSave,
                                    onRecordClick = { recordId ->
                                        analytics.logEvent(MapmoryAnalyticsEvent.JOURNAL_RECORD_OPENED)
                                        onRecordClick(recordId)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
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
private fun JournalHeader(recordCount: Int) {
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
                text = "TRAVEL ARCHIVE",
                color = TripRecordPalette.current.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "모든 여행 기록",
                color = TripRecordPalette.current.headingText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(35.dp)
                .background(TripRecordPalette.current.primarySoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = recordCount.toString(),
                color = TripRecordPalette.current.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
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
    LazyColumn(
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
            if (!failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = { save.progressPercent / 100f },
                        modifier = Modifier.size(32.dp),
                        color = TripRecordPalette.current.primary,
                        trackColor = TripRecordPalette.current.primarySoft,
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = "${save.progressPercent}%",
                        color = TripRecordPalette.current.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
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
                modifier = Modifier.size(76.dp),
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
            photo()
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
