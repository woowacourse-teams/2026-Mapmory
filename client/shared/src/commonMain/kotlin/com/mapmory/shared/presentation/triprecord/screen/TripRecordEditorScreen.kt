package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.presentation.photo.PhotoLibraryActionsFactory
import com.mapmory.shared.presentation.photo.PhotoLoadingProgress
import com.mapmory.shared.presentation.photo.PhotoRecommendationPagingState
import com.mapmory.shared.presentation.photo.RecommendationLoadKey
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.photo.accept
import com.mapmory.shared.presentation.photo.rememberPhotoLibraryActions
import com.mapmory.shared.presentation.photo.shouldLoadNextRecommendationPage
import com.mapmory.shared.presentation.photo.toggleSelection
import com.mapmory.shared.presentation.date.PlatformDatePicker
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorErrorTarget
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewLocations

private const val StartDatePickerTarget = "start"
private const val EndDatePickerTarget = "end"
private const val RecommendationGridPrefetchItems = 3
internal const val PhotoRecommendationGridTestTag = "photo-recommendation-grid"

private val EditorBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val trailingEdge = offset + size
        return when {
            offset < 0f && trailingEdge > containerSize -> 0f
            offset < 0f -> offset
            trailingEdge > containerSize -> trailingEdge - containerSize
            else -> 0f
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun TripRecordEditorScreen(
    uiState: TripRecordEditorUiState,
    locations: List<Location>,
    onLocationSelected: (Location) -> Unit,
    onLocationTouched: () -> Unit = {},
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onPhotosAdded: (List<SelectedPhoto>) -> Unit = {},
    onPhotoRemoved: (String) -> Unit = {},
    onPhotoLoadingChanged: (Boolean) -> Unit = {},
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    onMapClick: () -> Unit = {},
    onRecordClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    photoLibraryActionsFactory: PhotoLibraryActionsFactory =
        {
            onPicked,
            onRecommended,
            onMessage,
            onLoadingChanged,
            onLoadingProgressChanged,
            onRecommendationLoadingChanged,
        ->
            rememberPhotoLibraryActions(
                onPicked,
                onRecommended,
                onMessage,
                onLoadingChanged,
                onLoadingProgressChanged,
                onRecommendationLoadingChanged,
            )
        },
    modifier: Modifier = Modifier,
) {
    val selectableLocations = remember(locations) {
        locations
            .filter { it.type == LocationType.PROVINCE || it.type == LocationType.DISTRICT }
            .distinctBy(Location::regionCode)
    }
    var showLocationSheet by remember { mutableStateOf(false) }
    var locationSearchQuery by rememberSaveable { mutableStateOf("") }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    var recommendationPagingState by remember { mutableStateOf(PhotoRecommendationPagingState()) }
    var lastAutoLoadTriggerKey by remember { mutableStateOf<RecommendationLoadKey?>(null) }
    var showRecommendationSheet by remember { mutableStateOf(false) }
    var isPreparingRecommendationPhotos by remember { mutableStateOf(false) }
    var isRecommendationLoading by remember { mutableStateOf(false) }
    var photoLoadingProgress by remember { mutableStateOf<PhotoLoadingProgress?>(null) }
    var datePickerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val dismissKeyboardOnTap = rememberDismissKeyboardOnTapModifier()
    val photoLibrary = photoLibraryActionsFactory(
        { photos ->
            photoMessage = null
            onPhotosAdded(photos)
        },
        { page ->
            val nextState = recommendationPagingState.accept(page)
            if (nextState != null) {
                recommendationPagingState = nextState
                if (nextState.photos.isNotEmpty()) {
                    showRecommendationSheet = true
                    photoMessage = null
                } else {
                    showRecommendationSheet = false
                    photoMessage = "선택한 지역에서 촬영된 GPS 사진을 찾지 못했어요."
                }
            }
        },
        { photoMessage = it },
        { isLoading ->
            photoLoadingProgress = null
            onPhotoLoadingChanged(isLoading)
        },
        { progress -> photoLoadingProgress = progress },
        { isLoading -> isRecommendationLoading = isLoading },
    )
    val locationResultsListState = rememberLazyListState()
    val recommendationGridState = rememberLazyGridState()
    LaunchedEffect(recommendationPagingState.generation) {
        if (recommendationPagingState.generation != null) {
            recommendationGridState.scrollToItem(0)
        }
    }
    LaunchedEffect(
        showRecommendationSheet,
        recommendationPagingState.generation,
        recommendationPagingState.photos.size,
        recommendationPagingState.hasMore,
        isRecommendationLoading,
    ) {
        if (!showRecommendationSheet || isRecommendationLoading) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = recommendationGridState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            layoutInfo.totalItemsCount > 0 &&
                lastVisibleIndex >= layoutInfo.totalItemsCount - RecommendationGridPrefetchItems
        }.collect { isAtBottom ->
            val generation = recommendationPagingState.generation ?: return@collect
            val currentKey = RecommendationLoadKey(
                generation = generation,
                visibleCount = recommendationPagingState.photos.size,
            )
            if (shouldLoadNextRecommendationPage(
                    isAtBottom = isAtBottom,
                    isLoading = isRecommendationLoading,
                    hasMore = recommendationPagingState.hasMore,
                    lastTriggerKey = lastAutoLoadTriggerKey,
                    currentKey = currentKey,
                )
            ) {
                lastAutoLoadTriggerKey = currentKey
                photoLibrary.loadNextRecommendationPage()
            }
        }
    }
    val filteredLocations = remember(locationSearchQuery, selectableLocations) {
        selectableLocations.filter { location ->
            locationSearchQuery.isBlank() ||
                location.name.contains(locationSearchQuery, ignoreCase = true) ||
                location.regionCode.contains(locationSearchQuery, ignoreCase = true)
        }
    }

    TripRecordBackground(modifier = modifier.then(dismissKeyboardOnTap)) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                title = if (uiState.recordId == null) "기록 남기기" else "기록 수정하기",
                onBackClick = onBackClick,
                isSaveEnabled = uiState.isSaveEnabled,
                isSaving = uiState.isSaving,
                onSaveClick = onSaveClick,
            )
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides EditorBringIntoViewSpec,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .imePadding()
                        .navigationBarsPadding()
                        .clipToBounds(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 32.dp),
                    ) {
                        PhotoSection(
                            locationName = uiState.selectedLocation?.name ?: "여행 장소",
                            photos = uiState.selectedPhotos,
                            onAddClick = photoLibrary.pickFromGallery,
                            onRecommendClick = {
                                if (isRecommendationLoading) {
                                    photoLibrary.cancelRecommendation()
                                    photoMessage = "사진 불러오기를 중단했어요."
                                } else {
                                    val selectedLocation = uiState.selectedLocation
                                    if (selectedLocation == null) {
                                        photoMessage = "사진을 추천받으려면 장소를 먼저 선택해 주세요."
                                    } else {
                                        photoMessage = "${selectedLocation.name}에서 촬영된 사진을 찾고 있어요."
                                        recommendationPagingState = PhotoRecommendationPagingState()
                                        lastAutoLoadTriggerKey = null
                                        showRecommendationSheet = false
                                        val parentName = locations
                                            .firstOrNull { it.id == selectedLocation.parentId }
                                            ?.name
                                        photoLibrary.recommendForLocation(selectedLocation, parentName)
                                    }
                                }
                            },
                            onRemoveClick = onPhotoRemoved,
                            recommendationsAvailable = photoLibrary.recommendationsAvailable,
                            isLoading = uiState.isPhotoLoading,
                            isRecommendationLoading = isRecommendationLoading,
                            loadingProgress = photoLoadingProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
                        )
                        photoMessage?.let { message ->
                            Text(
                                text = message,
                                color = TripRecordPalette.muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                        EditorErrorMessage(
                            message = uiState.errorMessageFor(TripRecordEditorErrorTarget.PHOTOS),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                        )
                        EditorDivider()

                        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                            EditorTitleField(
                                value = uiState.title,
                                onValueChange = onTitleChanged,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            EditorErrorMessage(
                                message = uiState.errorMessageFor(TripRecordEditorErrorTarget.TITLE),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        EditorDivider(Modifier.padding(horizontal = 20.dp))

                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            LocationSelector(
                                selectedLocation = uiState.selectedLocation,
                                locations = locations,
                                onClick = {
                                    onLocationTouched()
                                    showLocationSheet = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            EditorErrorMessage(
                                message = uiState.errorMessageFor(TripRecordEditorErrorTarget.LOCATION),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        EditorDivider(Modifier.padding(horizontal = 20.dp))

                        DateFields(
                            startDate = uiState.startDate,
                            endDate = uiState.endDate,
                            startDateError = uiState.errorMessageFor(TripRecordEditorErrorTarget.START_DATE),
                            endDateError = uiState.errorMessageFor(TripRecordEditorErrorTarget.END_DATE),
                            onStartDateClick = { datePickerTarget = StartDatePickerTarget },
                            onEndDateClick = { datePickerTarget = EndDatePickerTarget },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                        EditorDivider(Modifier.padding(horizontal = 20.dp))

                        CompanionChips(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )

                        EditorContentField(
                            value = uiState.content,
                            onValueChange = onContentChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        )

                        uiState.generalErrorMessage?.takeIf { uiState.isDirty }?.let { message ->
                            EditorErrorMessage(
                                message = message,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLocationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLocationSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = TripRecordPalette.background,
            contentColor = TripRecordPalette.text,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .then(dismissKeyboardOnTap)
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = "장소 선택",
                    color = TripRecordPalette.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "국가, 시·도, 시·군·구를 검색해 보세요",
                    color = TripRecordPalette.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedTextField(
                    value = locationSearchQuery,
                    onValueChange = { locationSearchQuery = it },
                    placeholder = { Text("장소명 또는 코드 검색") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TripRecordPalette.text,
                        unfocusedTextColor = TripRecordPalette.text,
                        cursorColor = TripRecordPalette.accent,
                        focusedBorderColor = TripRecordPalette.accent,
                        unfocusedBorderColor = TripRecordPalette.line,
                        focusedPlaceholderColor = TripRecordPalette.muted,
                        unfocusedPlaceholderColor = TripRecordPalette.muted,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                Spacer(Modifier.height(14.dp))
                if (filteredLocations.isEmpty()) {
                    Text(
                        text = "검색 결과가 없습니다.",
                        color = TripRecordPalette.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 28.dp),
                    )
                } else {
                    LazyColumn(
                        state = locationResultsListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp)
                            .nestedScroll(consumeLocationListOverscrollConnection),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(filteredLocations) { location ->
                            LocationSearchResult(
                                location = location,
                                locations = locations,
                                selected = uiState.selectedLocation?.regionCode == location.regionCode,
                                onClick = {
                                    onLocationSelected(location)
                                    showLocationSheet = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRecommendationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRecommendationSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = TripRecordPalette.background,
            contentColor = TripRecordPalette.text,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text("이 장소에서 찍은 사진", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "사진의 EXIF GPS가 선택한 행정구역과 일치하는 결과예요.",
                    color = TripRecordPalette.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = recommendationGridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 360.dp)
                        .testTag(PhotoRecommendationGridTestTag),
                    contentPadding = PaddingValues(top = 18.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(
                        items = recommendationPagingState.photos,
                        key = SelectedPhoto::id,
                    ) { photo ->
                        val selected = photo.id in recommendationPagingState.selectedIds
                        RecommendedPhoto(
                            photo = photo,
                            selected = selected,
                            onClick = {
                                recommendationPagingState = recommendationPagingState.toggleSelection(photo.id)
                            },
                        )
                    }
                }
                if (isRecommendationLoading && recommendationPagingState.photos.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = TripRecordPalette.accent,
                            strokeWidth = 1.5.dp,
                        )
                        Text(
                            text = "사진을 더 불러오는 중…",
                            color = TripRecordPalette.muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                TextButton(
                    onClick = {
                        val selectedPhotos = recommendationPagingState.photos
                            .filter { it.id in recommendationPagingState.selectedIds }
                        isPreparingRecommendationPhotos = true
                        onPhotoLoadingChanged(true)
                        photoLibrary.prepareForAdding(selectedPhotos) { preparedPhotos ->
                            isPreparingRecommendationPhotos = false
                            onPhotoLoadingChanged(false)
                            if (preparedPhotos.isEmpty()) {
                                photoMessage = "선택한 사진의 원본을 읽지 못했어요."
                            } else {
                                onPhotosAdded(preparedPhotos)
                                showRecommendationSheet = false
                                photoMessage = null
                            }
                        }
                    },
                    enabled = recommendationPagingState.selectedIds.isNotEmpty() &&
                        !isPreparingRecommendationPhotos,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        if (isPreparingRecommendationPhotos) {
                            "원본 불러오는 중…"
                        } else {
                            "선택한 사진 추가"
                        },
                        color = TripRecordPalette.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    val activeDatePickerTarget = datePickerTarget
    val isStartDatePicker = activeDatePickerTarget == StartDatePickerTarget
    PlatformDatePicker(
        visible = activeDatePickerTarget != null,
        initialDate = when {
            isStartDatePicker -> uiState.startDate
            activeDatePickerTarget == EndDatePickerTarget -> uiState.endDate
            else -> null
        },
        minimumDate = if (activeDatePickerTarget == EndDatePickerTarget) {
            uiState.startDate
        } else {
            null
        },
        onDateSelected = { date ->
            if (isStartDatePicker) {
                onStartDateChanged(date)
            } else if (activeDatePickerTarget == EndDatePickerTarget) {
                onEndDateChanged(date)
            }
            datePickerTarget = null
        },
        onDismiss = { datePickerTarget = null },
    )
}

@Composable
private fun EditorTopBar(
    title: String,
    onBackClick: () -> Unit,
    isSaveEnabled: Boolean,
    isSaving: Boolean,
    onSaveClick: () -> Unit,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            TextButton(
                onClick = onBackClick,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(48.dp),
            ) {
                Text(
                    text = "←",
                    color = TripRecordPalette.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            Text(
                text = title,
                color = TripRecordPalette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
            TextButton(
                onClick = onSaveClick,
                enabled = isSaveEnabled,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = if (isSaving) "저장 중" else "저장",
                    color = if (isSaveEnabled) TripRecordPalette.accent else TripRecordPalette.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        EditorDivider()
    }
}

@Composable
private fun PhotoSection(
    locationName: String,
    photos: List<TripRecordPhotoUiState>,
    onAddClick: () -> Unit,
    onRecommendClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    recommendationsAvailable: Boolean,
    isLoading: Boolean,
    isRecommendationLoading: Boolean,
    loadingProgress: PhotoLoadingProgress? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$locationName · 여행 일정과 가까운 사진",
                color = TripRecordPalette.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = onRecommendClick,
                enabled = isRecommendationLoading || (recommendationsAvailable && !isLoading),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TripRecordPalette.photoRecommendBackground)
                    .border(
                        width = 1.dp,
                        color = TripRecordPalette.photoRecommendBorder,
                        shape = RoundedCornerShape(6.dp),
                    ),
            ) {
                if (isRecommendationLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = TripRecordPalette.photoRecommendText,
                            strokeWidth = 1.5.dp,
                        )
                        Text(
                            text = loadingProgress?.let { progress ->
                                progress.percentage?.let { percentage -> "중단 · $percentage%" }
                            } ?: "중단",
                            color = TripRecordPalette.photoRecommendText,
                            fontSize = 9.sp,
                        )
                    }
                } else {
                    Text(
                        text = "위치 기반 사진\n불러오기",
                        color = TripRecordPalette.photoRecommendText,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                    )
                }
            }
        }
        PhotoEditor(
            photos = photos,
            onAddClick = onAddClick,
            onRemoveClick = onRemoveClick,
            isAddEnabled = !isLoading,
            modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun EditorTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = TripRecordPalette.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(TripRecordPalette.accent),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(
                        text = "여행의 제목을 적어주세요",
                        color = TripRecordPalette.muted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier.height(34.dp),
    )
}

@Composable
private fun DateFields(
    startDate: String,
    endDate: String,
    startDateError: String?,
    endDateError: String?,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        DateField(
            label = "시작",
            value = startDate,
            errorMessage = startDateError,
            onClick = onStartDateClick,
            modifier = Modifier.weight(1f),
        )
        DateField(
            label = "종료",
            value = endDate,
            errorMessage = endDateError,
            onClick = onEndDateClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DateField(
    label: String,
    value: String,
    errorMessage: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            color = TripRecordPalette.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = value.ifBlank { "연도. 월. 일." },
                    color = if (value.isBlank()) TripRecordPalette.muted else TripRecordPalette.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "▦",
                    color = TripRecordPalette.muted,
                    fontSize = 16.sp,
                )
            }
        }
        EditorErrorMessage(
            message = errorMessage,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EditorErrorMessage(
    message: String?,
    modifier: Modifier = Modifier,
) {
    message ?: return
    Text(
        text = message,
        color = TripRecordPalette.danger,
        fontSize = 11.sp,
        modifier = modifier,
    )
}

private fun TripRecordEditorUiState.errorMessageFor(target: TripRecordEditorErrorTarget): String? =
    fieldErrors[target]?.takeIf {
        target == TripRecordEditorErrorTarget.PHOTOS || isFieldDirty(target)
    }

@Composable
private fun CompanionChips(modifier: Modifier = Modifier) {
    val companions = remember { listOf("가족", "애인", "친구", "혼자") }
    var selectedCompanion by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        companions.forEach { companion ->
            val selected = selectedCompanion == companion
            Text(
                text = companion,
                color = if (selected) TripRecordPalette.primary else TripRecordPalette.text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        if (selected) TripRecordPalette.primarySoft else TripRecordPalette.surface,
                    )
                    .border(1.dp, TripRecordPalette.line, RoundedCornerShape(50.dp))
                    .clickable {
                        selectedCompanion = if (selected) null else companion
                    }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun EditorContentField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = TripRecordPalette.text,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        ),
        cursorBrush = SolidColor(TripRecordPalette.accent),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = "이곳에서의 추억을 자유롭게 남겨보세요. (선택)",
                        color = TripRecordPalette.muted,
                        fontSize = 12.sp,
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier.heightIn(min = 150.dp, max = 200.dp),
    )
}

@Composable
private fun EditorDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TripRecordPalette.line.copy(alpha = 0.55f)),
    )
}

@Composable
private fun PhotoEditor(
    photos: List<TripRecordPhotoUiState>,
    onAddClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    isAddEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PhotoActionButton(onClick = onAddClick, enabled = isAddEnabled)
        photos.forEach { photo ->
            Box {
                PhotoPreview(photo = photo, modifier = Modifier.size(112.dp, 84.dp))
                Text(
                    text = "×",
                    color = TripRecordPalette.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onRemoveClick(photo.id) }
                        .background(TripRecordPalette.background.copy(alpha = 0.8f))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun PhotoActionButton(onClick: () -> Unit, enabled: Boolean) {
    Column(
        modifier = Modifier
            .size(112.dp, 84.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(TripRecordPalette.photoGalleryBackground)
            .border(1.dp, TripRecordPalette.photoGalleryBorder, RoundedCornerShape(14.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "사진첩",
            color = TripRecordPalette.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "전체 보기",
            color = TripRecordPalette.muted,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RecommendedPhoto(
    photo: SelectedPhoto,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag("photo-recommendation-item-${photo.id}")
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick),
        ) {
            PhotoPreview(photo, Modifier.fillMaxSize())
            if (selected) {
                Text(
                    "✓",
                    color = TripRecordPalette.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .background(TripRecordPalette.accent, RoundedCornerShape(20.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = photo.capturedAt ?: photo.displayName,
            color = TripRecordPalette.muted,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun PhotoPreview(photo: SelectedPhoto, modifier: Modifier = Modifier) {
    TripPhotoImage(
        imageBytes = photo.previewBytes,
        contentDescription = photo.displayName,
        modifier = modifier,
        placeholderVariant = photo.id.hashCode(),
    )
}

@Composable
private fun PhotoPreview(photo: TripRecordPhotoUiState, modifier: Modifier = Modifier) {
    TripPhotoImage(
        imageBytes = photo.previewBytes?.bytesForDecoding(),
        contentDescription = photo.displayName,
        modifier = modifier,
        placeholderVariant = photo.id.hashCode(),
    )
}
@Composable
private fun LocationSelector(
    selectedLocation: Location?,
    locations: List<Location>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = if (selectedLocation == null) {
                "여행 장소"
            } else if (selectedLocation.countryId == KoreaCountryId) {
                "국내 여행"
            } else {
                "해외 여행"
            },
            color = TripRecordPalette.accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⊙",
                color = TripRecordPalette.muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = selectedLocation?.displayName(locations) ?: "여행 장소를 선택해 주세요",
                color = if (selectedLocation == null) TripRecordPalette.muted else TripRecordPalette.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "⌄",
                color = TripRecordPalette.muted,
                fontSize = 14.sp,
            )
        }
    }
}

private fun Location.displayName(locations: List<Location>): String {
    if (type != LocationType.DISTRICT) return name
    val parentName = locations.firstOrNull { it.id == parentId }?.name ?: return name
    return "$parentName $name"
}

@Composable
private fun LocationSearchResult(
    location: Location,
    locations: List<Location>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(if (selected) TripRecordPalette.accentSoft else TripRecordPalette.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = location.name,
                color = TripRecordPalette.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${locationTypeLabel(location)} · ${locationContext(location, locations)}",
                color = TripRecordPalette.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (selected) {
            Text(
                text = "선택됨",
                color = TripRecordPalette.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(
    name = "여행 기록 작성",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordEditorScreenPreview() {
    PreviewSurface {
        TripRecordEditorScreen(
            uiState = TripRecordEditorUiState(
                selectedLocation = previewLocations[1],
                title = "봄날의 서울",
                content = "천천히 걸으며 발견한 서울의 새로운 모습",
                startDate = "2026-04-12",
                endDate = "2026-04-14",
            ),
            locations = previewLocations,
            onLocationSelected = {},
            onTitleChanged = {},
            onContentChanged = {},
            onStartDateChanged = {},
            onEndDateChanged = {},
            onSaveClick = {},
            onBackClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 작성 빈 상태",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun EmptyTripRecordEditorScreenPreview() {
    PreviewSurface {
        TripRecordEditorScreen(
            uiState = TripRecordEditorUiState(),
            locations = previewLocations,
            onLocationSelected = {},
            onTitleChanged = {},
            onContentChanged = {},
            onStartDateChanged = {},
            onEndDateChanged = {},
            onSaveClick = {},
            onBackClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 작성 오류",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun ErrorTripRecordEditorScreenPreview() {
    PreviewSurface {
        TripRecordEditorScreen(
            uiState = TripRecordEditorUiState(
                dirtyFields = setOf(
                    TripRecordEditorErrorTarget.LOCATION,
                    TripRecordEditorErrorTarget.TITLE,
                ),
                fieldErrors = mapOf(
                    TripRecordEditorErrorTarget.LOCATION to "여행 장소를 선택해 주세요.",
                    TripRecordEditorErrorTarget.TITLE to "제목을 입력해 주세요.",
                ),
            ),
            locations = previewLocations,
            onLocationSelected = {},
            onTitleChanged = {},
            onContentChanged = {},
            onStartDateChanged = {},
            onEndDateChanged = {},
            onSaveClick = {},
            onBackClick = {},
        )
    }
}

private fun locationTypeLabel(location: Location): String = when {
    location.countryId != KoreaCountryId -> "국가"
    location.type == LocationType.PROVINCE -> "시·도"
    else -> "시·군·구"
}

private fun locationContext(location: Location, locations: List<Location>): String = when {
    location.countryId != KoreaCountryId -> "세계 지도"
    location.type == LocationType.PROVINCE -> "대한민국"
    else -> locations.firstOrNull { it.id == location.parentId }?.name ?: "대한민국"
}

private val consumeLocationListOverscrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(x = 0f, y = available.y)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = Velocity(x = 0f, y = available.y)
}


private const val KoreaCountryId = 1L
