package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.lazy.rememberLazyListState
import com.mapmory.shared.presentation.components.ScrollToTopEffect

import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import com.mapmory.shared.presentation.components.MapmoryBackButton
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.mapmory.shared.data.settings.MemoryShareVideoJobStore
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.repository.ShareVideoRepository
import com.mapmory.shared.navigation.PlatformBackHandler
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.presentation.triprecord.TripRecordPhotoCollection
import com.mapmory.shared.presentation.triprecord.TripRecordListLayout
import com.mapmory.shared.presentation.triprecord.groupSecondaryTripRecordCollections
import com.mapmory.shared.presentation.triprecord.groupTripRecordsByLocation
import com.mapmory.shared.presentation.triprecord.state.TripRecordFilterUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoCollectionIndexState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoCollectionLoadState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewUiRecords

@Composable
internal fun TripRecordListScreen(
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
    shareVideoRepository: ShareVideoRepository? = null,
    shareVideoJobStore: ShareVideoJobStore? = null,
    layout: TripRecordListLayout = TripRecordListLayout.PHOTO,
    showLayoutSwitcher: Boolean = false,
    onLayoutChange: (TripRecordListLayout) -> Unit = {},
    backHandlerRegistry: MapmoryBackHandlerRegistry? = null,
    backHandlerOwnerId: String? = null,
    photoCollectionLoadState: TripRecordPhotoCollectionLoadState = TripRecordPhotoCollectionLoadState.Idle,
    photoCollectionIndexState: TripRecordPhotoCollectionIndexState = TripRecordPhotoCollectionIndexState.Idle,
    onLoadPhotoCollection: (String) -> Unit = {},
    onLoadPhotoCollectionIndex: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showTimelineVideo by rememberSaveable { mutableStateOf(false) }
    var selectedCollectionLocation by rememberSaveable { mutableStateOf<String?>(null) }
    var showCollectionPhotoGallery by rememberSaveable { mutableStateOf(false) }
    var gestureClosedCollectionLocation by remember { mutableStateOf<String?>(null) }
    var gestureClosedPhotoGallery by remember { mutableStateOf(false) }
    val videoCreationScope = rememberCoroutineScope()
    val analytics = LocalMapmoryAnalytics.current
    val collectionRecords = (photoCollectionIndexState as? TripRecordPhotoCollectionIndexState.Loaded)
        ?.records.orEmpty()
    val collections = remember(collectionRecords) { groupTripRecordsByLocation(collectionRecords) }
    val selectedCollection = collections.firstOrNull { it.locationName == selectedCollectionLocation }
    val loadedPhotoCollection = (photoCollectionLoadState as? TripRecordPhotoCollectionLoadState.Loaded)
        ?.takeIf { it.locationName == selectedCollectionLocation }
    val displayedCollection = loadedPhotoCollection?.let {
        TripRecordPhotoCollection(it.locationName, it.records)
    } ?: selectedCollection
    val photoCollectionStateLocation = when (val state = photoCollectionLoadState) {
        is TripRecordPhotoCollectionLoadState.Loading -> state.locationName
        is TripRecordPhotoCollectionLoadState.Loaded -> state.locationName
        is TripRecordPhotoCollectionLoadState.Error -> state.locationName
        TripRecordPhotoCollectionLoadState.Idle -> null
    }
    val latestBackAction = rememberUpdatedState {
        gestureClosedCollectionLocation = null
        gestureClosedPhotoGallery = false
        when {
            showCollectionPhotoGallery -> {
                showCollectionPhotoGallery = false
                gestureClosedPhotoGallery = true
                true
            }
            selectedCollectionLocation != null -> {
                gestureClosedCollectionLocation = selectedCollectionLocation
                selectedCollectionLocation = null
                true
            }
            else -> false
        }
    }
    val latestForwardAction = rememberUpdatedState {
        when {
            gestureClosedCollectionLocation != null -> {
                selectedCollectionLocation = gestureClosedCollectionLocation
                gestureClosedCollectionLocation = null
                true
            }
            gestureClosedPhotoGallery -> {
                showCollectionPhotoGallery = true
                gestureClosedPhotoGallery = false
                true
            }
            else -> false
        }
    }
    val openRecord: (Long) -> Unit = { recordId ->
        analytics.logEvent(
            MapmoryAnalyticsEvent.JOURNAL_RECORD_OPENED,
            mapOf("record_id" to recordId.toString(), "source" to "journal"),
        )
        onRecordClick(recordId)
    }
    val changeLayout: (TripRecordListLayout) -> Unit = { nextLayout ->
        if (nextLayout != TripRecordListLayout.COLLECTION) {
            selectedCollectionLocation = null
            showCollectionPhotoGallery = false
        }
        onLayoutChange(nextLayout)
    }
    PlatformBackHandler(enabled = selectedCollection != null) {
        latestBackAction.value()
    }
    LaunchedEffect(layout, selectedCollectionLocation, photoCollectionStateLocation) {
        val locationName = selectedCollectionLocation
        if (layout == TripRecordListLayout.COLLECTION &&
            locationName != null && photoCollectionStateLocation != locationName
        ) {
            onLoadPhotoCollection(locationName)
        }
    }
    DisposableEffect(backHandlerRegistry, backHandlerOwnerId) {
        val registry = backHandlerRegistry
        val ownerId = backHandlerOwnerId
        if (registry == null || ownerId == null) {
            return@DisposableEffect onDispose { }
        }
        val registration = registry.register(
            ownerId = ownerId,
            gestureBack = { latestBackAction.value() },
            gestureForward = { latestForwardAction.value() },
        ) { latestBackAction.value() }
        onDispose { registry.unregister(registration) }
    }
    val hasScrollableRecordList = (uiState as? TripRecordListUiState.Success)
        ?.let { it.totalElements > 0 || pendingSaves.isNotEmpty() } == true
    TripRecordBackground(
        modifier = modifier.then(rememberDismissKeyboardOnTapModifier()),
        backgroundColor = TripRecordPalette.current.pageBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (selectedCollection == null) {
                JournalHeader(
                    recordCount = (uiState as? TripRecordListUiState.Success)?.totalElements,
                    layout = layout,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (selectedCollection != null && layout == TripRecordListLayout.COLLECTION) {
                    if (showCollectionPhotoGallery) {
                        TripRecordPhotoCollectionGallery(
                            collection = selectedCollection,
                            photoCollectionLoadState = photoCollectionLoadState,
                            onBackClick = { showCollectionPhotoGallery = false },
                            onRetryClick = { onLoadPhotoCollection(selectedCollection.locationName) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        TripRecordPhotoCollectionDetail(
                            collection = displayedCollection ?: selectedCollection,
                            isLoadingAllPhotos = photoCollectionStateLocation == selectedCollection.locationName &&
                                loadedPhotoCollection == null &&
                                photoCollectionLoadState !is TripRecordPhotoCollectionLoadState.Error,
                            hasLoadedAllPhotos = loadedPhotoCollection != null,
                            onRetryLoadPhotos = { onLoadPhotoCollection(selectedCollection.locationName) },
                            onBackClick = { selectedCollectionLocation = null },
                            onShowAllPhotos = { showCollectionPhotoGallery = true },
                            onRecordClick = openRecord,
                            onCreateClick = onCreateClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                ) {
                    if (shareVideoRepository != null) {
                        TripTimelineVideoEntry(
                            onClick = {
                                analytics.logEvent(
                                    MapmoryAnalyticsEvent.VIDEO_OPENED,
                                    mapOf("source" to "journal"),
                                )
                                showTimelineVideo = true
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    if (showLayoutSwitcher && !hasScrollableRecordList) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            JournalLayoutSwitcher(layout = layout, onLayoutChange = changeLayout)
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
                            val isEmpty = when (layout) {
                                TripRecordListLayout.COLLECTION ->
                                    photoCollectionIndexState is TripRecordPhotoCollectionIndexState.Loaded &&
                                        collectionRecords.isEmpty() && pendingSaves.isEmpty()
                                else -> uiState.records.isEmpty() && pendingSaves.isEmpty()
                            }
                            if (isEmpty) {
                                EmptyTripRecords(
                                    hasFilter = filter.locationId != null || filter.selectedTagId != null,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                when (layout) {
                                    TripRecordListLayout.TIMELINE -> TripRecordTimelineList(
                                        records = uiState.records,
                                        pendingSaves = pendingSaves,
                                        onRetryPendingSave = onRetryPendingSave,
                                        onDismissPendingSave = onDismissPendingSave,
                                        onRecordClick = openRecord,
                                        showLayoutSwitcher = showLayoutSwitcher && hasScrollableRecordList,
                                        layout = layout,
                                        onLayoutChange = changeLayout,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TripRecordListLayout.PHOTO -> TripRecordPhotoList(
                                        records = uiState.records,
                                        pendingSaves = pendingSaves,
                                        onRetryPendingSave = onRetryPendingSave,
                                        onDismissPendingSave = onDismissPendingSave,
                                        onRecordClick = openRecord,
                                        showLayoutSwitcher = showLayoutSwitcher && hasScrollableRecordList,
                                        layout = layout,
                                        onLayoutChange = changeLayout,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TripRecordListLayout.COLLECTION -> TripRecordPhotoCollectionList(
                                        records = collectionRecords,
                                        pendingSaves = pendingSaves,
                                        onRetryPendingSave = onRetryPendingSave,
                                        onDismissPendingSave = onDismissPendingSave,
                                        onCollectionClick = {
                                            selectedCollectionLocation = it
                                            showCollectionPhotoGallery = false
                                        },
                                        showLayoutSwitcher = showLayoutSwitcher && hasScrollableRecordList,
                                        layout = layout,
                                        onLayoutChange = changeLayout,
                                        indexState = photoCollectionIndexState,
                                        onRetryIndex = onLoadPhotoCollectionIndex,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            if (layout != TripRecordListLayout.COLLECTION && uiState.totalPages > 1) {
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

                }
                if (selectedCollection == null) {
                    TripRecordCreateButton(
                        source = "journal_fab",
                        onClick = onCreateClick,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }

            if (selectedCollection == null || layout != TripRecordListLayout.COLLECTION) {
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

    if (showTimelineVideo && shareVideoRepository != null) {
        TripTimelineVideoDialog(
            repository = shareVideoRepository,
            jobStore = shareVideoJobStore ?: remember { MemoryShareVideoJobStore() },
            creationScope = videoCreationScope,
            onDismiss = { showTimelineVideo = false },
        )
    }
}

@Composable
private fun TripTimelineVideoEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, palette.border),
        colors = CardDefaults.cardColors(containerColor = palette.primarySoft),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(palette.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = palette.primary, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "일지 영상 만들기",
                    color = palette.headingText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "내 여행 기록을 타임라인 영상으로 모아요",
                    color = palette.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "›",
                color = palette.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun JournalLayoutSwitcher(
    layout: TripRecordListLayout,
    onLayoutChange: (TripRecordListLayout) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(TripRecordPalette.current.surfaceElevated)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(
            TripRecordListLayout.PHOTO to "사진",
            TripRecordListLayout.TIMELINE to "시간순",
            TripRecordListLayout.COLLECTION to "여행지별",
        ).forEach { (option, label) ->
            val selected = layout == option
            Text(
                text = label,
                color = if (selected) TripRecordPalette.current.primary else TripRecordPalette.current.secondaryText,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) TripRecordPalette.current.surface else TripRecordPalette.current.surfaceElevated)
                    .clickable(role = Role.Button) { onLayoutChange(option) }
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
private fun JournalHeader(recordCount: Long?, layout: TripRecordListLayout) {
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
                text = when (layout) {
                    TripRecordListLayout.TIMELINE -> "언제, 어디서 남긴 기록인지 차례로"
                    TripRecordListLayout.COLLECTION -> "여행 기록을 여행지별로 모아보기"
                    TripRecordListLayout.PHOTO -> "사진을 따라 그날의 기억으로"
                },
                color = TripRecordPalette.current.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        recordCount?.let {
            Text(
                text = "총 ${it}개",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
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
    layout: TripRecordListLayout,
    onLayoutChange: (TripRecordListLayout) -> Unit,
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
                    JournalLayoutSwitcher(layout = layout, onLayoutChange = onLayoutChange)
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
                            JournalLayoutSwitcher(layout = layout, onLayoutChange = onLayoutChange)
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
private fun TripRecordPhotoCollectionList(
    records: List<TripRecordItemUiState>,
    pendingSaves: List<BackgroundTripRecordSave>,
    onRetryPendingSave: (Long) -> Unit,
    onDismissPendingSave: (Long) -> Unit,
    onCollectionClick: (String) -> Unit,
    showLayoutSwitcher: Boolean,
    layout: TripRecordListLayout,
    onLayoutChange: (TripRecordListLayout) -> Unit,
    indexState: TripRecordPhotoCollectionIndexState,
    onRetryIndex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val collections = remember(records) { groupTripRecordsByLocation(records) }
    val gridRows = remember(collections) { groupSecondaryTripRecordCollections(collections) }
    ScrollToTopEffect(listState)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    JournalLayoutSwitcher(layout = layout, onLayoutChange = onLayoutChange)
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
        when (indexState) {
            TripRecordPhotoCollectionIndexState.Idle,
            TripRecordPhotoCollectionIndexState.Loading,
            -> item(key = "photo-collection-loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TripRecordPalette.current.primary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "전체 여행지를 불러오고 있어요",
                        modifier = Modifier.padding(start = 10.dp),
                        color = TripRecordPalette.current.secondaryText,
                        fontSize = 12.sp,
                    )
                }
            }
            is TripRecordPhotoCollectionIndexState.Error -> item(key = "photo-collection-error") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        indexState.message,
                        color = TripRecordPalette.current.secondaryText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetryIndex) {
                        Text("다시 불러오기", color = TripRecordPalette.current.primary)
                    }
                }
            }
            is TripRecordPhotoCollectionIndexState.Loaded -> Unit
        }
        if (collections.isNotEmpty()) {
            item(key = "photo-collection-heading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "여행지별 사진 모음",
                        color = TripRecordPalette.current.headingText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${collections.size}곳 · 사진 ${collections.sumOf(TripRecordPhotoCollection::photoCount)}장",
                        color = TripRecordPalette.current.secondaryText,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        collections.firstOrNull()?.let { featuredCollection ->
            item(key = "featured-${featuredCollection.locationName}") {
                TripRecordPhotoCollectionCard(
                    collection = featuredCollection,
                    featured = true,
                    onClick = { onCollectionClick(featuredCollection.locationName) },
                )
            }
        }
        items(gridRows, key = { row -> "collections-${row.first().locationName}" }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { collection ->
                    TripRecordPhotoCollectionCard(
                        collection = collection,
                        modifier = Modifier.weight(1f),
                        onClick = { onCollectionClick(collection.locationName) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TripRecordPhotoCollectionDetail(
    collection: TripRecordPhotoCollection,
    isLoadingAllPhotos: Boolean,
    hasLoadedAllPhotos: Boolean,
    onRetryLoadPhotos: () -> Unit,
    onBackClick: () -> Unit,
    onShowAllPhotos: () -> Unit,
    onRecordClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverRecord = collection.records.firstOrNull()
    val coverPhoto = coverRecord?.photos?.minByOrNull(TripRecordPhotoUiState::sortOrder)
    val photoEntries = remember(collection.records) { collection.photoEntries }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy((-24).dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(252.dp)
                .background(TripRecordPalette.current.imagePlaceholder),
        ) {
            if (coverRecord != null && coverPhoto != null) {
                CollectionPhotoTile(
                    photo = coverPhoto,
                    record = coverRecord,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 20.dp, bottom = 42.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "TRAVEL COLLECTION",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    if (collection.records.size == 2) {
                        "${collection.locationName}의 두 장면"
                    } else {
                        "${collection.locationName}의 여행"
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "기록 ${collection.records.size}개",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 12.sp,
                )
            }
            MapmoryBackButton(
                onClick = onBackClick,
                color = TripRecordPalette.current.headingText,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(TripRecordPalette.current.surface.copy(alpha = 0.92f), CircleShape),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(TripRecordPalette.current.pageBackground)
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 4.dp),
            ) {
                item(key = "records-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "여행 모아보기",
                            color = TripRecordPalette.current.headingText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${collection.records.size}개 일지",
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 11.sp,
                        )
                    }
                }
                items(collection.records, key = TripRecordItemUiState::id) { record ->
                    PhotoCollectionTripRecordCard(
                        record = record,
                        onClick = { onRecordClick(record.id) },
                    )
                }
                if (photoEntries.isNotEmpty() || collection.photoCount > 0) {
                    item(key = "photos") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "사진으로 이어보기",
                                    color = TripRecordPalette.current.headingText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = {
                                        if (isLoadingAllPhotos) onShowAllPhotos()
                                        else {
                                            onRetryLoadPhotos()
                                            onShowAllPhotos()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        Text(
                                            when {
                                                isLoadingAllPhotos -> "사진 모으는 중"
                                                hasLoadedAllPhotos -> "사진 ${photoEntries.size}장 모두 보기"
                                                else -> "사진 모두 보기"
                                            },
                                            color = TripRecordPalette.current.primary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                        Text(
                                            "›",
                                            color = TripRecordPalette.current.primary,
                                            fontSize = 14.sp,
                                            lineHeight = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.alignByBaseline(),
                                        )
                                    }
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                modifier = Modifier.fillMaxWidth().height(76.dp),
                            ) {
                                itemsIndexed(
                                    items = photoEntries,
                                    key = { _, (record, photo) -> "${record.id}-${photo.id}" },
                                ) { index, (record, photo) ->
                                    Box(
                                        modifier = Modifier
                                            .size(width = 92.dp, height = 76.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                    ) {
                                        CollectionPhotoTile(
                                            photo = photo,
                                            record = record,
                                            modifier = Modifier.fillMaxSize(),
                                            index = index,
                                        )
                                    }
                                }
                                if (photoEntries.isEmpty() && isLoadingAllPhotos) {
                                    item {
                                        Box(
                                            modifier = Modifier.size(width = 92.dp, height = 76.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = TripRecordPalette.current.primary,
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onCreateClick,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TripRecordPalette.current.primary,
                    contentColor = TripRecordPalette.current.onPrimary,
                ),
            ) {
                Text("새 기록 추가")
            }
        }
    }
}

@Composable
private fun TripRecordPhotoCollectionGallery(
    collection: TripRecordPhotoCollection,
    photoCollectionLoadState: TripRecordPhotoCollectionLoadState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadState = when (val state = photoCollectionLoadState) {
        is TripRecordPhotoCollectionLoadState.Loading -> state.takeIf { it.locationName == collection.locationName }
        is TripRecordPhotoCollectionLoadState.Loaded -> state.takeIf { it.locationName == collection.locationName }
        is TripRecordPhotoCollectionLoadState.Error -> state.takeIf { it.locationName == collection.locationName }
        TripRecordPhotoCollectionLoadState.Idle -> null
    }
    val loadedRecords = (loadState as? TripRecordPhotoCollectionLoadState.Loaded)?.records
    val photoEntries = remember(loadedRecords) {
        loadedRecords.orEmpty().flatMap { record ->
            record.photos.sortedBy(TripRecordPhotoUiState::sortOrder).map { record to it }
        }
    }
    Column(
        modifier = modifier.background(TripRecordPalette.current.pageBackground),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapmoryBackButton(
                onClick = onBackClick,
                color = TripRecordPalette.current.headingText,
                modifier = Modifier.background(TripRecordPalette.current.surface, CircleShape),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    collection.locationName,
                    color = TripRecordPalette.current.headingText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "사진 모아보기",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 12.sp,
                )
            }
            when (loadState) {
                is TripRecordPhotoCollectionLoadState.Loaded -> Text(
                    "${photoEntries.size}장",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                is TripRecordPhotoCollectionLoadState.Error -> TextButton(
                    onClick = onRetryClick,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) { Text("다시 시도", color = TripRecordPalette.current.primary, fontSize = 12.sp) }
                else -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = TripRecordPalette.current.primary,
                    strokeWidth = 2.dp,
                )
            }
        }
        when (loadState) {
            is TripRecordPhotoCollectionLoadState.Loaded -> if (photoEntries.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("이 지역에 모인 사진이 없어요.", color = TripRecordPalette.current.secondaryText)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    gridItemsIndexed(
                        items = photoEntries,
                        key = { _, (record, photo) -> "${record.id}-${photo.id}" },
                    ) { index, (record, photo) ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp)),
                        ) {
                            CollectionPhotoTile(
                                photo = photo,
                                record = record,
                                modifier = Modifier.fillMaxSize(),
                                index = index,
                            )
                        }
                    }
                }
            }
            is TripRecordPhotoCollectionLoadState.Error -> Box(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(loadState.message, color = TripRecordPalette.current.secondaryText, textAlign = TextAlign.Center)
                    Button(onClick = onRetryClick) { Text("다시 불러오기") }
                }
            }
            else -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TripRecordPalette.current.primary)
            }
        }
    }
}

@Composable
private fun TripRecordPhotoCollectionCard(
    collection: TripRecordPhotoCollection,
    featured: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (featured) 232.dp else 178.dp)
            .clip(RoundedCornerShape(if (featured) 22.dp else 18.dp))
            .background(TripRecordPalette.current.imagePlaceholder)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        TripRecordCollectionMosaic(
            collection = collection,
            modifier = Modifier.fillMaxSize(),
            featured = featured,
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.02f), Color.Transparent, Color.Black.copy(alpha = 0.76f)),
                ),
            ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (featured) {
                Text(
                    "MAPMORY JOURNAL",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
            }
            Text(
                collection.locationName,
                color = Color.White,
                fontSize = if (featured) 24.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "기록 ${collection.records.size}개 · 사진 ${collection.photoCount}장",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = if (featured) 12.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!featured) {
                Text(
                    collection.records.firstOrNull()?.let(::recordDateRange) ?: "날짜 미정",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TripRecordCollectionMosaic(
    collection: TripRecordPhotoCollection,
    modifier: Modifier = Modifier,
    featured: Boolean = false,
) {
    val photos = remember(collection.records) {
        collection.records.mapNotNull { record ->
            record.photos.minByOrNull(TripRecordPhotoUiState::sortOrder)?.let { record to it }
        }.take(4)
    }
    Box(
        modifier = modifier
            .background(TripRecordPalette.current.imagePlaceholder),
    ) {
        if (featured) {
            photos.firstOrNull()?.let { (record, photo) ->
                CollectionPhotoTile(photo, record, Modifier.fillMaxSize())
            } ?: Text(
                "사진 없음",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else when (photos.size) {
            0 -> Text(
                "사진 없음",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            1 -> CollectionPhotoTile(photos[0].second, photos[0].first, Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                photos.forEachIndexed { index, (record, photo) ->
                    CollectionPhotoTile(photo, record, Modifier.weight(1f).fillMaxSize(), index)
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                photos.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        row.forEachIndexed { columnIndex, (record, photo) ->
                            CollectionPhotoTile(
                                photo,
                                record,
                                Modifier.weight(1f).fillMaxSize(),
                                rowIndex * 2 + columnIndex,
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
private fun PhotoCollectionTripRecordCard(
    record: TripRecordItemUiState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TripRecordPhotoMosaic(record, Modifier.size(104.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    recordDateRange(record),
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.title.ifBlank { record.locationName },
                    color = TripRecordPalette.current.headingText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    record.locationName,
                    color = TripRecordPalette.current.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.content.isNotBlank()) {
                    Text(
                        record.content,
                        color = TripRecordPalette.current.bodyText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "사진 ${record.photoCount ?: record.photos.size}장",
                    color = TripRecordPalette.current.secondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", color = TripRecordPalette.current.secondaryText, fontSize = 22.sp)
        }
    }
}

@Composable
private fun TripRecordPhotoMosaic(
    record: TripRecordItemUiState,
    modifier: Modifier = Modifier,
) {
    val photos = remember(record.photos) {
        record.photos.sortedBy(TripRecordPhotoUiState::sortOrder).take(4)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TripRecordPalette.current.imagePlaceholder),
    ) {
        when (photos.size) {
            0 -> Text(
                "사진 없음",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center),
            )

            1 -> CollectionPhotoTile(
                photo = photos[0],
                record = record,
                modifier = Modifier.fillMaxSize(),
            )

            2 -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                photos.forEachIndexed { index, photo ->
                    CollectionPhotoTile(
                        photo = photo,
                        record = record,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        index = index,
                    )
                }
            }

            else -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                photos.chunked(2).forEachIndexed { rowIndex, rowPhotos ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        rowPhotos.forEachIndexed { columnIndex, photo ->
                            CollectionPhotoTile(
                                photo = photo,
                                record = record,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                index = rowIndex * 2 + columnIndex,
                            )
                        }
                        if (rowPhotos.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionPhotoTile(
    photo: TripRecordPhotoUiState,
    record: TripRecordItemUiState,
    modifier: Modifier = Modifier,
    index: Int = 0,
) {
    MapmoryAsyncImage(
        imageBytes = photo.previewBytes?.bytesForDecoding(),
        imageUri = photo.previewUri,
        fallbackUri = photo.fullResolutionUri,
        contentDescription = "${record.locationName} 사진 ${index + 1}",
        modifier = modifier,
        shape = RoundedCornerShape(0.dp),
    )
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
    layout: TripRecordListLayout,
    onLayoutChange: (TripRecordListLayout) -> Unit,
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
                    JournalLayoutSwitcher(layout = layout, onLayoutChange = onLayoutChange)
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
    val date = record.startDate?.takeIf(String::isNotBlank)
        ?: record.endDate?.takeIf(String::isNotBlank)
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
    name = "여행 기록 여행지별 보기",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordCollectionListScreenPreview() {
    PreviewSurface {
        TripRecordListScreen(
            uiState = TripRecordListUiState.Success(previewUiRecords, page = 0, totalPages = 3),
            filter = TripRecordFilterUiState(),
            layout = TripRecordListLayout.COLLECTION,
            photoCollectionIndexState = TripRecordPhotoCollectionIndexState.Loaded(previewUiRecords),
            showLayoutSwitcher = true,
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
    name = "여행 지역 상세",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordCollectionDetailScreenPreview() {
    val collection = TripRecordPhotoCollection("서울", previewUiRecords)
    PreviewSurface {
        TripRecordPhotoCollectionDetail(
            collection = collection,
            isLoadingAllPhotos = false,
            hasLoadedAllPhotos = false,
            onRetryLoadPhotos = {},
            onBackClick = {},
            onShowAllPhotos = {},
            onRecordClick = {},
            onCreateClick = {},
            modifier = Modifier.fillMaxSize(),
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
