package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.components.MapmoryPhotoExpansion
import com.mapmory.shared.presentation.components.PhotoViewerOriginalCache
import com.mapmory.shared.presentation.components.PhotoListScrollBar
import com.mapmory.shared.presentation.components.ScrollToTopEffect
import com.mapmory.shared.presentation.components.MapmoryPhotoViewer
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import com.mapmory.shared.presentation.components.LocalMapmoryImageTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.analytics.emit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import com.mapmory.shared.presentation.triprecord.state.localOriginalUri
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.photo.rememberPhotoLibraryActions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewUiRecords
import kotlinx.datetime.LocalDate

@Composable
fun TripRecordDetailScreen(
    uiState: TripRecordDetailUiState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMapClick: () -> Unit = {},
    onRecordClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onInternalBackHandlerChanged: ((() -> Boolean)?) -> Unit = {},
    onForwardHandlerChanged: ((() -> Boolean)?) -> Unit = {},
    modifier: Modifier = Modifier,
    initialLocationName: String? = null,
    initialLatestDate: String? = null,
    initialPhotoCount: Int? = null,
    isTimelineLayout: Boolean = false,
) {
    val analytics = LocalMapmoryAnalytics.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDetailActions by rememberSaveable { mutableStateOf(false) }
    var expandedPhotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var forwardPhotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val albumScrollState = rememberLazyListState()
    ScrollToTopEffect(albumScrollState, enabled = expandedPhotoIndex == null && uiState is TripRecordDetailUiState.Success)
    var photoColumns by rememberSaveable { mutableStateOf(2) }
    val latestPhotoViewerBackHandler by rememberUpdatedState {
        if (showDetailActions) {
            showDetailActions = false
            true
        } else if (expandedPhotoIndex != null) {
            forwardPhotoIndex = expandedPhotoIndex
            expandedPhotoIndex = null
            true
        } else false
    }

    DisposableEffect(expandedPhotoIndex, showDetailActions) {
        onInternalBackHandlerChanged(
            if (expandedPhotoIndex == null && !showDetailActions) null else latestPhotoViewerBackHandler,
        )
        onDispose { onInternalBackHandlerChanged(null) }
    }

    val latestForwardHandler by rememberUpdatedState {
        val index = forwardPhotoIndex
        val photoCount = (uiState as? TripRecordDetailUiState.Success)?.record?.photos?.size ?: 0
        if (expandedPhotoIndex == null && index != null && index in 0 until photoCount) {
            expandedPhotoIndex = index
            forwardPhotoIndex = null
            true
        } else false
    }
    DisposableEffect(Unit) {
        onForwardHandlerChanged { latestForwardHandler() }
        onDispose { onForwardHandlerChanged(null) }
    }

    TripRecordBackground(modifier = modifier) {
        when (uiState) {
            TripRecordDetailUiState.Idle,
            TripRecordDetailUiState.Loading,
            -> TripRecordDetailSkeleton(
                modifier = Modifier.fillMaxSize(),
                locationName = initialLocationName,
                latestDate = initialLatestDate,
                photoCount = initialPhotoCount,
                columnCount = photoColumns,
                onBackClick = onBackClick,
            )

            TripRecordDetailUiState.Deleting -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = TripRecordPalette.current.accent)
            }

            is TripRecordDetailUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(uiState.message, color = TripRecordPalette.current.danger)
                TextButton(onClick = onBackClick) { Text("목록으로") }
            }

            is TripRecordDetailUiState.Success -> {
                val record = uiState.record
                var selectedTimelineYear by rememberSaveable(record.id) { mutableStateOf("전체") }
                val groups = remember(record.id, record.photos, record.startDate) {
                    groupTripRecordPhotosByDate(record.photos)
                }
                val orderedPhotos = remember(groups) { groups.flatMap(TripRecordPhotoGroup::photos) }
                val selectedIndex = expandedPhotoIndex

                MapmoryPhotoExpansion(selectedIndex) { targetIndex ->
                    if (targetIndex != null && orderedPhotos.isNotEmpty()) {
                        ExpandedTripPhotoViewer(
                            locationName = record.locationName,
                            photos = orderedPhotos,
                            initialPage = targetIndex.coerceIn(orderedPhotos.indices),
                            onBackClick = { expandedPhotoIndex = null },
                        )
                    } else {
                        val openPhoto: (TripRecordPhotoUiState) -> Unit = { photo ->
                            analytics.emit(MapmoryAnalyticsEvent.RECORD_PHOTO_OPENED, mapOf("record_id" to record.id.toString()))
                            forwardPhotoIndex = null
                            expandedPhotoIndex = orderedPhotos.indexOfFirst { it.id == photo.id }
                                .takeIf { it >= 0 }
                        }
                        if (isTimelineLayout) {
                            TripRecordPhotoTimeline(
                                record = record,
                                groups = groups,
                                scrollState = albumScrollState,
                                selectedYear = selectedTimelineYear,
                                onSelectedYearChange = { selectedTimelineYear = it },
                                onBackClick = onBackClick,
                                onActionMenuOpen = { showDetailActions = true },
                                onPhotoClick = openPhoto,
                            )
                        } else {
                            TripRecordPhotoAlbum(
                                record = record,
                                groups = groups,
                                scrollState = albumScrollState,
                                columnCount = photoColumns,
                                onColumnCountChange = { photoColumns = it },
                                onBackClick = onBackClick,
                                onActionMenuOpen = { showDetailActions = true },
                                onPhotoClick = openPhoto,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDetailActions) {
        Dialog(
            onDismissRequest = { showDetailActions = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().clickable(onClick = { showDetailActions = false }))
                DetailActionCardMenu(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 62.dp, end = 22.dp),
                    onEditClick = {
                        showDetailActions = false
                        onEditClick()
                    },
                    onDeleteClick = {
                        showDetailActions = false
                        showDeleteDialog = true
                    },
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("여행 기록 삭제") },
            text = { Text("삭제한 기록은 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteClick()
                    },
                ) {
                    Text("삭제", color = TripRecordPalette.current.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun RecordSharePreviewAction(record: TripRecordItemUiState) {
    var showPreview by rememberSaveable(record.id) { mutableStateOf(false) }
    TripSharePreviewButton(
        contentDescription = "여행 기록 공유 미리보기",
        onClick = { showPreview = true },
    )
    if (showPreview) {
        TripRecordSharePreviewDialog(record = record, onDismiss = { showPreview = false })
    }
}

@Composable
private fun TripRecordPhotoAlbum(
    record: TripRecordItemUiState,
    groups: List<TripRecordPhotoGroup>,
    onBackClick: () -> Unit,
    onActionMenuOpen: () -> Unit,
    scrollState: LazyListState,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    onPhotoClick: (TripRecordPhotoUiState) -> Unit,
) {
    var isPinching by remember { mutableStateOf(false) }
    val photoRows = remember(groups, columnCount) {
        groups.map { group -> group.photos.chunked(columnCount) }
    }
    Column(Modifier.fillMaxSize()) {
        TripRecordTopBar(
            title = "",
            onBackClick = onBackClick,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecordSharePreviewAction(record)
                    DetailMoreButton(onClick = onActionMenuOpen)
                }
            },
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val photoSize = animateDpAsState(
                targetValue = ((maxWidth - 56.dp - 8.dp * (columnCount - 1)) / columnCount)
                    .coerceAtLeast(1.dp),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "albumPhotoSize",
            )
            LazyColumn(
                state = scrollState,
                userScrollEnabled = !isPinching,
                contentPadding = PaddingValues(bottom = 36.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .photoGridPinch(columnCount, onColumnCountChange, { isPinching = it })
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "editorial-heading", contentType = "heading") {
                    AlbumHeading(
                        locationName = record.locationName,
                        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 24.dp),
                    )
                }
                if (record.content.isNotBlank()) {
                    item(key = "editorial-note", contentType = "note") {
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .background(TripRecordPalette.current.primarySoft, RoundedCornerShape(16.dp))
                                .padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                "그날의 메모",
                                color = TripRecordPalette.current.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                record.content,
                                color = TripRecordPalette.current.headingText,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                            )
                        }
                    }
                }
                item(key = "editorial-gallery-heading", contentType = "heading") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "함께 남긴 사진",
                            color = TripRecordPalette.current.headingText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${record.photoCount ?: record.photos.size}장",
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (record.photos.isEmpty()) {
                    item(key = "empty") { EmptyPhotoAlbum() }
                } else {
                    groups.forEachIndexed { index, group ->
                        item(key = "date:${group.displayDate}", contentType = "date") {
                            Text(
                                text = group.displayDate,
                                color = TripRecordPalette.current.headingText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.animateItem().padding(horizontal = 20.dp)
                                    .padding(top = if (index == 0) 2.dp else 14.dp),
                            )
                        }
                        itemsIndexed(
                            photoRows[index],
                            key = { _, photos -> "row:${photos.first().id}" },
                            contentType = { _, _ -> "photos" },
                        ) { rowIndex, photos ->
                            AlbumPhotoRow(
                                photos, group.displayDate, columnCount, { photoSize.value }, onPhotoClick,
                                Modifier.animateItem().padding(horizontal = 20.dp)
                                    .padding(top = if (rowIndex == 0) 0.dp else 8.dp),
                            )
                        }
                    }
                }
            }
            if (groups.isNotEmpty()) {
                PhotoListScrollBar(
                    listState = scrollState,
                    contentKey = groups to columnCount,
                    testTag = "album-scrollbar",
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 5.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TripRecordPhotoTimeline(
    record: TripRecordItemUiState,
    groups: List<TripRecordPhotoGroup>,
    scrollState: LazyListState,
    selectedYear: String,
    onSelectedYearChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onActionMenuOpen: () -> Unit,
    onPhotoClick: (TripRecordPhotoUiState) -> Unit,
) {
    val timelineGroups = remember(groups) {
        groups.sortedWith(
            compareBy<TripRecordPhotoGroup> { it.sortDate == null }
                .thenBy(TripRecordPhotoGroup::sortDate),
        ).map { group ->
            group.copy(
                photos = group.photos.sortedWith(
                    compareBy<TripRecordPhotoUiState> { it.capturedAt.isNullOrBlank() }
                        .thenBy { it.capturedAt.orEmpty() }
                        .thenBy(TripRecordPhotoUiState::sortOrder),
                ),
            )
        }
    }
    val availableYears = remember(timelineGroups) {
        timelineGroups.mapNotNull { it.sortDate?.take(4) }.distinct().sortedDescending()
    }
    val hasUndatedPhotos = timelineGroups.any { it.sortDate == null }
    val yearOptions = remember(availableYears, hasUndatedPhotos) {
        listOf("전체") + availableYears + if (hasUndatedPhotos) listOf("날짜 미정") else emptyList()
    }
    LaunchedEffect(record.id, yearOptions) {
        if (selectedYear !in yearOptions) onSelectedYearChange("전체")
    }
    val visibleGroups = remember(timelineGroups, selectedYear) {
        when (selectedYear) {
            "전체" -> timelineGroups
            "날짜 미정" -> timelineGroups.filter { it.sortDate == null }
            else -> timelineGroups.filter { it.sortDate?.startsWith(selectedYear) == true }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TripRecordTopBar(
            title = "",
            onBackClick = onBackClick,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecordSharePreviewAction(record)
                    DetailMoreButton(onClick = onActionMenuOpen)
                }
            },
        )
        LazyColumn(
            state = scrollState,
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "timeline-heading") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        record.title,
                        color = TripRecordPalette.current.headingText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${record.locationName} · ${recordDateRange(record)} · 사진 ${record.photoCount ?: record.photos.size}장",
                        color = TripRecordPalette.current.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (record.content.isNotBlank()) {
                item(key = "timeline-note") {
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .background(TripRecordPalette.current.primarySoft, RoundedCornerShape(16.dp))
                            .padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "기록 메모",
                            color = TripRecordPalette.current.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            record.content,
                            color = TripRecordPalette.current.headingText,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
            item(key = "timeline-list-heading") {
                Text(
                    "시간순 기록",
                    color = TripRecordPalette.current.headingText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 3.dp),
                )
            }
            if (yearOptions.size > 1) {
                item(key = "timeline-year-filter") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "연도별로 보기",
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            yearOptions.forEach { year ->
                                val selected = selectedYear == year
                                val shape = RoundedCornerShape(50)
                                val label = year.toIntOrNull()?.let { "${year}년" } ?: year
                                Row(
                                    modifier = Modifier
                                        .clip(shape)
                                        .background(
                                            if (selected) TripRecordPalette.current.primary
                                            else TripRecordPalette.current.surface,
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) TripRecordPalette.current.primary
                                            else TripRecordPalette.current.border,
                                            shape,
                                        )
                                        .selectable(
                                            selected = selected,
                                            role = Role.RadioButton,
                                            onClick = { onSelectedYearChange(year) },
                                        )
                                        .heightIn(min = 44.dp)
                                        .padding(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (selected) {
                                        Text(
                                            "✓",
                                            color = TripRecordPalette.current.onPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        label,
                                        color = if (selected) TripRecordPalette.current.onPrimary
                                        else TripRecordPalette.current.headingText,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (visibleGroups.isEmpty()) {
                item(key = "timeline-empty") { EmptyPhotoAlbum() }
            } else {
                visibleGroups.forEachIndexed { index, group ->
                    item(key = "timeline-${group.displayDate}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.width(25.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(24.dp)
                                        .background(TripRecordPalette.current.primarySoft, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color = TripRecordPalette.current.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (index < visibleGroups.lastIndex) {
                                    Box(
                                        Modifier.width(1.dp).height(98.dp)
                                            .background(TripRecordPalette.current.border),
                                    )
                                }
                            }
                            Column(
                                Modifier.weight(1f).padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            group.displayDate,
                                            color = TripRecordPalette.current.headingText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            record.locationName,
                                            color = TripRecordPalette.current.secondaryText,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    Text(
                                        "${group.photos.size}장",
                                        color = TripRecordPalette.current.secondaryText,
                                        fontSize = 11.sp,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(
                                        androidx.compose.foundation.rememberScrollState(),
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    group.photos.forEach { photo ->
                                        MapmoryAsyncImage(
                                            imageBytes = photo.previewBytes?.bytesForDecoding()
                                                ?: photo.originalBytes?.bytesForDecoding(),
                                            imageUri = photo.previewUri ?: photo.localOriginalUri
                                                ?: photo.fullResolutionUri,
                                            cacheKey = "trip-preview:${photo.id}",
                                            fallbackUri = photo.fullResolutionUri,
                                            fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                                            contentDescription = "${group.displayDate} 여행 사진 확대",
                                            sharedImageKey = photo.id,
                                            sharedCornerRadius = 10.dp,
                                            onClick = { onPhotoClick(photo) },
                                            modifier = Modifier.size(width = 64.dp, height = 64.dp),
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlbumHeading(
    locationName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${locationName.takeIf { it.isNotBlank() } ?: "여행"} 사진첩",
                color = TripRecordPalette.current.headingText,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "날짜별로 모아둔 여행 사진이에요.",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun EmptyPhotoAlbum() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "이 기록에 추가된 사진이 없어요.",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun AlbumPhotoRow(
    photos: List<TripRecordPhotoUiState>,
    displayDate: String,
    columnCount: Int,
    photoSize: () -> Dp,
    onPhotoClick: (TripRecordPhotoUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEach { photo ->
            MapmoryAsyncImage(
                imageBytes = photo.previewBytes?.bytesForDecoding()
                    ?: photo.originalBytes?.bytesForDecoding(),
                imageUri = photo.previewUri ?: photo.localOriginalUri ?: photo.fullResolutionUri,
                cacheKey = "trip-preview:${photo.id}",
                fallbackUri = photo.fullResolutionUri,
                fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                contentDescription = "$displayDate 여행 사진 확대",
                sharedImageKey = photo.id,
                sharedCornerRadius = 14.dp,
                onClick = { onPhotoClick(photo) },
                modifier = Modifier
                    .layout { measurable, _ ->
                        val side = photoSize().roundToPx().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(side, side) { placeable.placeRelative(0, 0) }
                    }
                    .semantics {
                        contentDescription = "$displayDate 여행 사진 확대"
                    },
                shape = RoundedCornerShape(14.dp),
            )
        }
        repeat(columnCount - photos.size) {
            Spacer(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val side = photoSize().roundToPx().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(side, side) { placeable.placeRelative(0, 0) }
                    },
            )
        }
    }
}


@Composable
private fun ExpandedTripPhotoViewer(
    locationName: String,
    photos: List<TripRecordPhotoUiState>,
    initialPage: Int,
    onBackClick: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { photos.size }
    val imageTransitionActive =
        LocalMapmoryImageTransitionScope.current?.isTransitionActive == true
    val originals = remember { PhotoViewerOriginalCache() }
    val photoLibrary = rememberPhotoLibraryActions({}, {}, {}, {}, {}, {}, {})
    DisposableEffect(Unit) {
        onDispose { photoLibrary.cancelFullResolutionPreview() }
    }
    LaunchedEffect(pagerState.currentPage, photos, imageTransitionActive) {
        if (imageTransitionActive) return@LaunchedEffect
        try {
            originals.prepare(photos.map { it.id }, pagerState.currentPage) { id ->
                val photo = photos.first { it.id == id }
                val localId = photo.localPhotoId
                if (localId != null && photo.localOriginalUri == null && photo.originalBytes == null) {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { photoLibrary.cancelFullResolutionPreview() }
                        photoLibrary.loadFullResolutionPreview(
                            SelectedPhoto(localId, photo.displayName, null),
                        ) { bytes ->
                            if (continuation.isActive) continuation.resume(bytes)
                        }
                    }
                } else null
            }
        } finally {
            photoLibrary.cancelFullResolutionPreview()
        }
    }

    var currentPhotoZoom by remember { mutableStateOf(1f) }
    MapmoryPhotoViewer(
        title = locationName,
        canDragDown = { currentPhotoZoom <= 1f && !pagerState.isScrollInProgress },
        showTopBar = currentPhotoZoom <= 1f,
        onClose = onBackClick,
        closeContentDescription = "사진첩으로 돌아가기",
        trailingContent = {
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { photos[it].id },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val photo = photos[page]
            var zoom by remember(photo.id) { mutableStateOf(1f) }
            var pan by remember(photo.id) { mutableStateOf(Offset.Zero) }
            var viewportSize by remember(photo.id) { mutableStateOf(IntSize.Zero) }
            val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
                val nextZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                val maxPanX = viewportSize.width * (nextZoom - 1f) / 2f
                val maxPanY = viewportSize.height * (nextZoom - 1f) / 2f
                zoom = nextZoom
                pan = if (nextZoom == 1f) Offset.Zero else Offset(
                    x = (pan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                    y = (pan.y + panChange.y).coerceIn(-maxPanY, maxPanY),
                )
            }
            val isCurrent = page == pagerState.currentPage
            androidx.compose.runtime.SideEffect {
                if (isCurrent) currentPhotoZoom = zoom
            }
            val localBytes = originals[photo.id]
            val awaitingLocalPhoto = !originals.contains(photo.id) &&
                photo.localPhotoId != null && photo.localOriginalUri == null && photo.originalBytes == null
            // A page keeps the same original request when becoming current or adjacent.
            val usePreview = imageTransitionActive || awaitingLocalPhoto
            Box(Modifier.fillMaxSize()) {
                MapmoryAsyncImage(
                    imageBytes = if (usePreview) {
                        photo.previewBytes?.bytesForDecoding() ?: photo.originalBytes?.bytesForDecoding()
                    } else {
                        localBytes ?: photo.originalBytes?.bytesForDecoding()
                            ?: photo.previewBytes?.bytesForDecoding()
                    },
                    imageUri = when {
                        usePreview -> photo.previewUri ?: photo.localOriginalUri
                        localBytes != null || photo.originalBytes != null -> null
                        else -> photo.localOriginalUri ?: photo.fullResolutionUri ?: photo.previewUri
                    },
                    cacheKey = if (usePreview) "trip-preview:${photo.id}" else "trip-full:${photo.id}",
                    blackLoadingBackground = true,
                    decodeOriginalSize = !usePreview,
                    fallbackUri = when {
                        usePreview -> null
                        photo.localOriginalUri != null -> photo.fullResolutionUri ?: photo.previewUri
                        else -> photo.previewUri
                    },
                    fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                    contentDescription = "$locationName 확대 사진 ${page + 1}",
                    sharedImageKey = photo.id.takeIf { isCurrent },
                    imageIdentity = photo.id,
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onSizeChanged { viewportSize = it }
                        .transformable(
                            state = transformableState,
                            canPan = { zoom > 1f },
                            lockRotationOnZoomPan = true,
                        )
                        .pointerInput(photo.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoom > 1f) {
                                        zoom = 1f
                                        pan = Offset.Zero
                                    } else {
                                        zoom = 2f
                                    }
                                },
                            )
                        }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                    shape = RectangleShape,
                    sharedCornerRadius = 14.dp,
                    contentScale = ContentScale.Fit,
                )
            }
        }

    }
}

@Composable
private fun DetailMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TripIconButton(
        label = "•••",
        contentDescription = "더보기",
        onClick = onClick,
        containerColor = Color.Transparent,
        contentColor = TripRecordPalette.current.text,
        modifier = modifier,
    )
}

@Composable
private fun DetailActionCardMenu(
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    val shape = RoundedCornerShape(19.dp)
    Column(
        modifier = modifier
            .width(208.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetailActionCardRow("기록 수정", isDestructive = false, onClick = onEditClick)
        DetailActionCardRow("기록 삭제", isDestructive = true, onClick = onDeleteClick)
    }
}

@Composable
private fun DetailActionCardRow(
    label: String,
    isDestructive: Boolean,
    onClick: () -> Unit,
) {
    val palette = TripRecordPalette.current
    val shape = RoundedCornerShape(12.dp)
    val tint = if (isDestructive) palette.danger else palette.accent
    val background = if (isDestructive) palette.danger.copy(alpha = 0.08f) else palette.primarySoft

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(if (isDestructive) tint.copy(alpha = 0.12f) else palette.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DetailActionGlyph(isDestructive = isDestructive, tint = tint)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (isDestructive) palette.danger else palette.headingText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DetailActionGlyph(
    isDestructive: Boolean,
    tint: Color,
) {
    Canvas(Modifier.size(17.dp)) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        if (isDestructive) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width * 0.28f, size.height * 0.32f),
                size = Size(size.width * 0.44f, size.height * 0.52f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = stroke,
            )
            drawLine(tint, Offset(size.width * 0.2f, size.height * 0.25f), Offset(size.width * 0.8f, size.height * 0.25f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.4f, size.height * 0.16f), Offset(size.width * 0.6f, size.height * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.43f, size.height * 0.45f), Offset(size.width * 0.43f, size.height * 0.7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.57f, size.height * 0.45f), Offset(size.width * 0.57f, size.height * 0.7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        } else {
            drawLine(tint, Offset(size.width * 0.28f, size.height * 0.72f), Offset(size.width * 0.7f, size.height * 0.3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.64f, size.height * 0.24f), Offset(size.width * 0.76f, size.height * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.24f, size.height * 0.76f), Offset(size.width * 0.42f, size.height * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.24f, size.height * 0.76f), Offset(size.width * 0.28f, size.height * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}

internal data class TripRecordPhotoGroup(
    val sortDate: String?,
    val displayDate: String,
    val photos: List<TripRecordPhotoUiState>,
)

internal fun groupTripRecordPhotosByDate(
    photos: List<TripRecordPhotoUiState>,
): List<TripRecordPhotoGroup> {
    return photos
        .groupBy { photo -> photo.capturedAt.toAlbumDate() }
        .map { (date, groupedPhotos) ->
            TripRecordPhotoGroup(
                sortDate = date,
                displayDate = date?.toAlbumDisplayDate() ?: UnknownPhotoDate,
                photos = groupedPhotos.sortedBy(TripRecordPhotoUiState::sortOrder),
            )
        }
        .sortedWith(
            compareByDescending<TripRecordPhotoGroup> { it.sortDate != null }
                .thenByDescending(TripRecordPhotoGroup::sortDate),
        )
}

internal fun String?.toAlbumDate(): String? {
    val value = this?.trim().orEmpty()
    val match = AlbumDatePattern.find(value) ?: return null
    val year = match.groupValues[1]
    val month = match.groupValues[2].padStart(2, '0')
    val day = match.groupValues[3].padStart(2, '0')
    return "$year-$month-$day".takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
}

internal fun String.toAlbumDisplayDate(): String {
    val (year, month, day) = split('-')
    val weekday = runCatching { LocalDate.parse(this).dayOfWeek.ordinal }
        .getOrNull()
        ?.let(KoreanWeekdays::getOrNull)
    return "$year. $month. $day${weekday?.let { " ($it)" }.orEmpty()}"
}

@Preview(
    name = "여행 기록 사진첩",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordDetailScreenPreview() {
    PreviewSurface {
        TripRecordDetailScreen(
            uiState = TripRecordDetailUiState.Success(previewUiRecords.first()),
            onBackClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 상세 로딩",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun LoadingTripRecordDetailScreenPreview() {
    PreviewSurface {
        TripRecordDetailScreen(
            uiState = TripRecordDetailUiState.Loading,
            onBackClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 상세 오류",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun ErrorTripRecordDetailScreenPreview() {
    PreviewSurface {
        TripRecordDetailScreen(
            uiState = TripRecordDetailUiState.Error("여행 기록을 불러오지 못했어요."),
            onBackClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}

private const val UnknownPhotoDate = "날짜 미상"
private val AlbumDatePattern = Regex("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})")
private val KoreanWeekdays = listOf("월", "화", "수", "목", "금", "토", "일")
