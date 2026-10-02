package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.TripRecordPhotoRules
import com.mapmory.shared.presentation.photo.PhotoLibraryActionsFactory
import com.mapmory.shared.presentation.photo.PhotoLibraryPermissionIssue
import com.mapmory.shared.presentation.photo.PhotoLoadingProgress
import com.mapmory.shared.presentation.photo.PhotoRecommendationPagingState
import com.mapmory.shared.presentation.photo.RecommendationLoadKey
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.photo.accept
import com.mapmory.shared.presentation.photo.rememberPhotoLibraryActions
import com.mapmory.shared.presentation.photo.setSelection
import com.mapmory.shared.presentation.photo.shouldLoadNextRecommendationPage
import com.mapmory.shared.presentation.photo.toggleSelection
import com.mapmory.shared.presentation.triprecord.selectableTripRecordDestinations
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private enum class NewRecordFlowStep {
    LOCATION,
    PHOTO_LOADING,
    PHOTO_PICKER,
}

private const val KoreaCountryId = 1L
private const val PhotoListPrefetchGroups = 2
private const val PhotoLimitMessageDurationMillis = 3_000L

private class PhotoDragSelectionController(
    private val selectedIds: () -> Set<String>,
    private val onSelectionChanged: (photoId: String, selected: Boolean) -> Unit,
) {
    private val boundsByPhotoId = mutableMapOf<String, Rect>()
    private val changedPhotoIds = mutableSetOf<String>()
    private var initialSelectedIds = emptySet<String>()
    private var currentSelectedIds = mutableSetOf<String>()
    private var startPosition: Offset? = null
    private var selectionValue: Boolean? = null

    fun updateBounds(photoId: String, bounds: Rect) {
        boundsByPhotoId[photoId] = bounds
    }

    fun remove(photoId: String) {
        boundsByPhotoId.remove(photoId)
    }

    fun start(photoId: String, positionInRoot: Offset) {
        changedPhotoIds.clear()
        initialSelectedIds = selectedIds().toSet()
        currentSelectedIds = initialSelectedIds.toMutableSet()
        startPosition = positionInRoot
        selectionValue = photoId !in initialSelectedIds
        moveTo(positionInRoot)
    }

    fun moveTo(positionInRoot: Offset) {
        val start = startPosition ?: return
        val selected = selectionValue ?: return
        val selectionRect = Rect(
            left = minOf(start.x, positionInRoot.x),
            top = minOf(start.y, positionInRoot.y),
            right = maxOf(start.x, positionInRoot.x),
            bottom = maxOf(start.y, positionInRoot.y),
        )
        boundsByPhotoId.forEach { (photoId, bounds) ->
            val inside = bounds.intersects(selectionRect)
            if (inside) changedPhotoIds += photoId
            if (inside || photoId in changedPhotoIds) {
                val desired = if (inside) selected else photoId in initialSelectedIds
                if ((photoId in currentSelectedIds) != desired) {
                    if (desired) currentSelectedIds += photoId else currentSelectedIds -= photoId
                    onSelectionChanged(photoId, desired)
                }
            }
        }
    }

    fun finish() {
        startPosition = null
        selectionValue = null
        initialSelectedIds = emptySet()
        currentSelectedIds.clear()
        changedPhotoIds.clear()
    }
}

private fun Rect.intersects(other: Rect): Boolean =
    left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

@Composable
internal fun NewTripRecordFlowScreen(
    uiState: TripRecordEditorUiState,
    locations: List<Location>,
    onLocationSelected: (Location) -> Unit,
    onLocationCleared: () -> Unit,
    onLocationTouched: () -> Unit,
    onPhotosAdded: (List<SelectedPhoto>) -> Unit,
    onPhotoRemoved: (String) -> Unit,
    onPhotoLoadingChanged: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    onInternalBackHandlerChanged: (handler: (() -> Boolean)?) -> Unit = {},
    photoLibraryActionsFactory: PhotoLibraryActionsFactory =
        {
            onPicked,
            onRecommended,
            onMessage,
            onLoadingChanged,
            onLoadingProgressChanged,
            onRecommendationLoadingChanged,
            onPermissionRequired,
        ->
            rememberPhotoLibraryActions(
                onPicked,
                onRecommended,
                onMessage,
                onLoadingChanged,
                onLoadingProgressChanged,
                onRecommendationLoadingChanged,
                onPermissionRequired,
            )
        },
    modifier: Modifier = Modifier,
) {
    val analytics = LocalMapmoryAnalytics.current
    val keyboard = LocalSoftwareKeyboardController.current
    val selectableLocations = remember(locations) {
        locations.selectableTripRecordDestinations()
    }
    var stepName by rememberSaveable { mutableStateOf(NewRecordFlowStep.LOCATION.name) }
    val step = NewRecordFlowStep.valueOf(stepName)
    val interactedFields = remember { mutableSetOf<String>() }
    var locationSearchQuery by rememberSaveable { mutableStateOf("") }
    var detailsErrorMessage by remember { mutableStateOf<String?>(null) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    var transientPhotoMessage by remember { mutableStateOf<String?>(null) }
    var transientPhotoMessageVersion by remember { mutableStateOf(0) }
    var photoLoadingProgress by remember { mutableStateOf<PhotoLoadingProgress?>(null) }
    var isPreparingPhotoPreviews by remember { mutableStateOf(false) }
    var isRecommendationLoading by remember { mutableStateOf(false) }
    var photoPermissionIssue by remember { mutableStateOf<PhotoLibraryPermissionIssue?>(null) }
    var recommendationPagingState by remember {
        mutableStateOf(PhotoRecommendationPagingState())
    }
    var lastAutoLoadTriggerKey by remember { mutableStateOf<RecommendationLoadKey?>(null) }
    var previewPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLocation by remember { mutableStateOf<Location?>(null) }
    var pendingSave by remember { mutableStateOf(false) }
    val photoListState = rememberLazyListState()

    fun logFieldInteraction(fieldName: String) {
        if (interactedFields.add(fieldName)) {
            analytics.logEvent(
                MapmoryAnalyticsEvent.RECORD_EDITOR_FIELD_INTERACTED,
                mapOf("field_name" to fieldName),
            )
        }
    }

    LaunchedEffect(step) {
        val stepName = when (step) {
            NewRecordFlowStep.LOCATION -> "location"
            NewRecordFlowStep.PHOTO_LOADING -> "photo_loading"
            NewRecordFlowStep.PHOTO_PICKER -> "photo_picker"
        }
        analytics.logEvent(
            MapmoryAnalyticsEvent.RECORD_FLOW_STEP_VIEWED,
            mapOf("step_name" to stepName),
        )
    }

    fun showPhotoLimitMessage() {
        transientPhotoMessage = TripRecordPhotoRules.LimitMessage
        transientPhotoMessageVersion += 1
    }

    LaunchedEffect(transientPhotoMessageVersion) {
        if (transientPhotoMessage == null) return@LaunchedEffect
        delay(PhotoLimitMessageDurationMillis)
        transientPhotoMessage = null
    }

    fun replaceEditorPhotos(photos: List<SelectedPhoto>) {
        uiState.selectedPhotos.forEach { photo -> onPhotoRemoved(photo.id) }
        onPhotosAdded(photos)
    }

    val photoLibrary = photoLibraryActionsFactory(
        { photos ->
            val acceptedPhotos = photos.take(TripRecordPhotoRules.MaxPhotosPerRecord)
            if (photos.size > acceptedPhotos.size) showPhotoLimitMessage()
            if (acceptedPhotos.isNotEmpty()) {
                analytics.logEvent(
                    MapmoryAnalyticsEvent.PHOTOS_ADDED,
                    mapOf("source" to "gallery", "count" to acceptedPhotos.size.toString()),
                )
                recommendationPagingState = PhotoRecommendationPagingState(
                    photos = acceptedPhotos,
                    selectedIds = acceptedPhotos.mapTo(mutableSetOf()) { it.id },
                )
                replaceEditorPhotos(acceptedPhotos)
                photoMessage = null
                pendingSave = true
            }
        },
        { page ->
            recommendationPagingState
                .accept(page, autoSelectNewPhotos = false)
                ?.let { nextState ->
                    recommendationPagingState = nextState
                    if (stepName == NewRecordFlowStep.PHOTO_LOADING.name) {
                        isPreparingPhotoPreviews = false
                        stepName = NewRecordFlowStep.PHOTO_PICKER.name
                    }
                    if (nextState.photos.isEmpty() && !page.hasMore) {
                        photoMessage = "선택한 장소에서 촬영된 사진을 찾지 못했어요."
                    } else if (nextState.photos.isNotEmpty()) {
                        photoMessage = null
                    }
                }
        },
        { message -> photoMessage = message },
        { isLoading ->
            onPhotoLoadingChanged(isLoading)
        },
        { progress ->
            photoLoadingProgress = progress
            isPreparingPhotoPreviews = progress.total > 0 && progress.processed >= progress.total
        },
        { isLoading -> isRecommendationLoading = isLoading },
        { issue -> photoPermissionIssue = issue },
    )

    val filteredLocations = remember(locationSearchQuery, selectableLocations, locations) {
        val query = locationSearchQuery.trim()
        if (query.isEmpty()) {
            emptyList()
        } else {
            selectableLocations.filter { location ->
                location.name.contains(query, ignoreCase = true) ||
                    location.flowDisplayName(locations).contains(query, ignoreCase = true) ||
                    location.regionCode.contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(uiState.selectedLocation?.id) {
        uiState.selectedLocation?.let { selected ->
            locationSearchQuery = selected.flowDisplayName(locations)
        }
    }

    LaunchedEffect(
        step,
        recommendationPagingState.generation,
        recommendationPagingState.photos.size,
        recommendationPagingState.hasMore,
        isRecommendationLoading,
    ) {
        if (step != NewRecordFlowStep.PHOTO_PICKER || isRecommendationLoading) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val info = photoListState.layoutInfo
            val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 &&
                lastVisibleIndex >= info.totalItemsCount - PhotoListPrefetchGroups
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

    fun returnToPreviousStep() {
        if (uiState.isSaving) return
        if (previewPhotoId != null) {
            previewPhotoId = null
            return
        }
        when (step) {
            NewRecordFlowStep.LOCATION -> onBackClick()
            NewRecordFlowStep.PHOTO_LOADING,
            NewRecordFlowStep.PHOTO_PICKER,
            -> {
                if (step == NewRecordFlowStep.PHOTO_LOADING) {
                    analytics.logEvent(MapmoryAnalyticsEvent.PHOTO_RECOMMENDATION_CANCELLED)
                }
                photoLibrary.cancelRecommendation()
                isPreparingPhotoPreviews = false
                onPhotoLoadingChanged(false)
                stepName = NewRecordFlowStep.LOCATION.name
            }
        }
    }

    val latestInternalBackHandler by rememberUpdatedState {
        returnToPreviousStep()
        true
    }
    DisposableEffect(step) {
        onInternalBackHandlerChanged(
            if (step == NewRecordFlowStep.LOCATION) null else ({ latestInternalBackHandler() }),
        )
        onDispose {
            onInternalBackHandlerChanged(null)
        }
    }

    fun beginPhotoSearch() {
        val location = uiState.selectedLocation
        when {
            location == null -> {
                onLocationTouched()
                detailsErrorMessage = "장소를 선택해 주세요."
            }
            !photoLibrary.recommendationsAvailable -> {
                recommendationPagingState = PhotoRecommendationPagingState()
                photoMessage = "이 기기에서는 위치로 사진을 찾을 수 없어요. 사진첩에서 직접 골라주세요."
                stepName = NewRecordFlowStep.PHOTO_PICKER.name
            }
            else -> {
                logFieldInteraction("photos")
                detailsErrorMessage = null
                photoMessage = null
                photoLoadingProgress = null
                isPreparingPhotoPreviews = false
                recommendationPagingState = PhotoRecommendationPagingState()
                lastAutoLoadTriggerKey = null
                stepName = NewRecordFlowStep.PHOTO_LOADING.name
                analytics.logEvent(
                    MapmoryAnalyticsEvent.PHOTO_RECOMMENDATION_STARTED,
                    mapOf("location_type" to location.type.name.lowercase()),
                )
                val parentName = locations.firstOrNull { it.id == location.parentId }?.name
                photoLibrary.recommendForLocation(location, parentName)
            }
        }
    }

    LaunchedEffect(pendingLocation, uiState.selectedLocation) {
        if (pendingLocation != null && pendingLocation == uiState.selectedLocation) {
            pendingLocation = null
            beginPhotoSearch()
        }
    }

    // Gallery callbacks may arrive before their loading=false callback.
    LaunchedEffect(pendingSave, uiState.isPhotoLoading, uiState.selectedPhotos) {
        if (pendingSave && !uiState.isPhotoLoading && uiState.selectedPhotos.isNotEmpty()) {
            pendingSave = false
            stepName = NewRecordFlowStep.PHOTO_PICKER.name
            onSaveClick()
        }
    }

    fun completePhotoSelection() {
        if (uiState.isSaving || pendingSave) return
        val selectedPhotos = recommendationPagingState.photos.filter { photo ->
            photo.id in recommendationPagingState.selectedIds
        }
        if (selectedPhotos.isEmpty()) {
            photoMessage = "앨범에 넣을 사진을 한 장 이상 선택해 주세요."
            return
        }
        analytics.logEvent(
            MapmoryAnalyticsEvent.PHOTOS_ADDED,
            mapOf("source" to "recommendation", "count" to selectedPhotos.size.toString()),
        )
        replaceEditorPhotos(selectedPhotos)
        photoMessage = null
        pendingSave = true
    }

    TripRecordBackground(
        modifier = modifier,
        backgroundColor = TripRecordPalette.current.pageBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            when (step) {
            NewRecordFlowStep.LOCATION -> LocationStep(
                uiState = uiState,
                locations = locations,
                searchQuery = locationSearchQuery,
                searchResults = filteredLocations,
                errorMessage = detailsErrorMessage,
                onSearchQueryChanged = {
                    onLocationTouched()
                    if (it.isNotBlank()) logFieldInteraction("location")
                    locationSearchQuery = it
                    val selectedName = uiState.selectedLocation?.flowDisplayName(locations)
                    if (selectedName != null && it != selectedName) onLocationCleared()
                    detailsErrorMessage = null
                },
                onLocationSelected = { location ->
                    logFieldInteraction("location")
                    analytics.logEvent(
                        MapmoryAnalyticsEvent.RECORD_LOCATION_SELECTED,
                        mapOf(
                            "source" to "location_search",
                            "location_type" to location.type.name.lowercase(),
                        ),
                    )
                    keyboard?.hide()
                    onLocationSelected(location)
                    pendingLocation = location
                    locationSearchQuery = location.flowDisplayName(locations)
                    detailsErrorMessage = null
                },
                onBackClick = ::returnToPreviousStep,
                onCompleteClick = ::beginPhotoSearch,
            )

            NewRecordFlowStep.PHOTO_LOADING -> PhotoLoadingStep(
                locationName = uiState.selectedLocation?.name.orEmpty(),
                progress = photoLoadingProgress,
                isPreparingPreviews = isPreparingPhotoPreviews,
                message = photoMessage,
                onBackClick = ::returnToPreviousStep,
                onPickFromGallery = {
                    logFieldInteraction("photos")
                    analytics.logEvent(MapmoryAnalyticsEvent.PHOTO_PICKER_OPENED)
                    photoLibrary.pickFromGallery()
                },
                onRetry = ::beginPhotoSearch,
            )

            NewRecordFlowStep.PHOTO_PICKER -> PhotoPickerStep(
                locationName = uiState.selectedLocation?.name ?: "여행지",
                pagingState = recommendationPagingState,
                listState = photoListState,
                message = uiState.errorMessage ?: photoMessage,
                isLoadingMore = isRecommendationLoading,
                isPreparing = uiState.isSaving || pendingSave,
                onBackClick = ::returnToPreviousStep,
                onCompleteClick = ::completePhotoSelection,
                onPickFromGallery = {
                    logFieldInteraction("photos")
                    analytics.logEvent(MapmoryAnalyticsEvent.PHOTO_PICKER_OPENED)
                    photoLibrary.pickFromGallery()
                },
                onPhotoPreview = { previewPhotoId = it.id },
                onPhotoToggle = { photo ->
                    val next = recommendationPagingState.toggleSelection(photo.id)
                    if (next == recommendationPagingState && photo.id !in next.selectedIds) {
                        previewPhotoId = null
                        showPhotoLimitMessage()
                    }
                    recommendationPagingState = next
                },
                onPhotoSelectionChange = { photoId, selected ->
                    val next = recommendationPagingState.setSelection(photoId, selected)
                    if (
                        selected &&
                        next == recommendationPagingState &&
                        photoId !in recommendationPagingState.selectedIds
                    ) {
                        showPhotoLimitMessage()
                    }
                    recommendationPagingState = next
                },
                onGroupToggle = { ids ->
                    val nextState = recommendationPagingState.toggleGroup(ids)
                    recommendationPagingState = nextState
                    if (
                        ids.any { it !in nextState.selectedIds } &&
                        nextState.selectedIds.size == nextState.maxSelectionCount
                    ) {
                        showPhotoLimitMessage()
                    }
                },
                onAllToggle = {
                    val allIds = recommendationPagingState.photos.map(SelectedPhoto::id)
                    val nextState = recommendationPagingState.toggleGroup(allIds)
                    recommendationPagingState = nextState
                    if (
                        allIds.any { it !in nextState.selectedIds } &&
                        nextState.selectedIds.size == nextState.maxSelectionCount
                    ) {
                        showPhotoLimitMessage()
                    }
                },
            )

            }

            transientPhotoMessage?.let { message ->
                FlowToast(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        }
    }

    photoPermissionIssue?.let { issue ->
        PhotoPermissionDialog(
            issue = issue,
            onOpenSettings = {
                photoPermissionIssue = null
                photoLibrary.openAppSettings()
            },
            onExit = {
                photoPermissionIssue = null
                photoMessage = "사진 접근을 허용하면 여행 사진을 자동으로 찾을 수 있어요."
            },
        )
    }

    previewPhotoId
        ?.let { id -> recommendationPagingState.photos.firstOrNull { it.id == id } }
        ?.let { photo ->
            PhotoPreviewDialog(
                photo = photo,
                selected = photo.id in recommendationPagingState.selectedIds,
                onToggle = {
                    val next = recommendationPagingState.toggleSelection(photo.id)
                    if (next == recommendationPagingState && photo.id !in next.selectedIds) {
                        previewPhotoId = null
                        showPhotoLimitMessage()
                    }
                    recommendationPagingState = next
                },
                onDismiss = { previewPhotoId = null },
            )
        }

}

@Composable
private fun LocationStep(
    uiState: TripRecordEditorUiState,
    locations: List<Location>,
    searchQuery: String,
    searchResults: List<Location>,
    errorMessage: String?,
    onSearchQueryChanged: (String) -> Unit,
    onLocationSelected: (Location) -> Unit,
    onBackClick: () -> Unit,
    onCompleteClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(
            title = "사진 불러오기",
            onBackClick = onBackClick,
            actionLabel = "완료",
            onActionClick = onCompleteClick,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = "NEW ALBUM",
                color = TripRecordPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = "어디 사진을 불러올까요?",
                color = TripRecordPalette.current.headingText,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = "해당 장소에서 찍은 사진을 불러와줘요.",
                color = TripRecordPalette.current.bodyText,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            FlowSectionTitle(
                title = "장소",
                badge = "필수",
                helper = "한 글자부터 검색할 수 있어요.",
                modifier = Modifier.padding(top = 30.dp),
            )
            LocationSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.padding(top = 12.dp),
            )
            if (searchQuery.isNotBlank()) {
                LocationSearchResults(
                    results = searchResults,
                    locations = locations,
                    selectedLocationId = uiState.selectedLocation?.id,
                    onLocationSelected = onLocationSelected,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = TripRecordPalette.current.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Text(
                text = "사진 불러오기",
                color = TripRecordPalette.current.onPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TripRecordPalette.current.primary)
                    .clickable(onClick = onCompleteClick)
                    .padding(vertical = 18.dp),
            )
        }
    }
}

@Composable
private fun FlowTopBar(
    title: String,
    onBackClick: () -> Unit,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onActionClick: () -> Unit = {},
) {
    Column {
        TripRecordTopBar(
            title = title,
            onBackClick = onBackClick,
            trailing = actionLabel?.let { label ->
                {
                    TextButton(
                        onClick = onActionClick,
                        enabled = actionEnabled,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (actionEnabled) {
                                TripRecordPalette.current.accent
                            } else {
                                TripRecordPalette.current.muted
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TripRecordPalette.current.line),
        )
    }
}

@Composable
private fun FlowSectionTitle(
    title: String,
    badge: String,
    helper: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            color = TripRecordPalette.current.headingText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = badge,
            color = TripRecordPalette.current.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = helper,
            color = TripRecordPalette.current.secondaryText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LocationSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(TripRecordPalette.current.surfaceElevated, RoundedCornerShape(18.dp))
            .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchIcon(
            color = TripRecordPalette.current.muted,
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = TripRecordPalette.current.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            cursorBrush = SolidColor(TripRecordPalette.current.accent),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            text = "도시 또는 국가 검색",
                            color = TripRecordPalette.current.muted,
                            fontSize = 15.sp,
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .testTag("new-record-location-search"),
        )
    }
}

@Composable
private fun SearchIcon(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension * 0.28f
        val center = Offset(size.width * 0.43f, size.height * 0.43f)
        drawCircle(color, radius, center, style = Stroke(stroke))
        drawLine(
            color = color,
            start = center + Offset(radius * 0.72f, radius * 0.72f),
            end = Offset(size.width * 0.86f, size.height * 0.86f),
            strokeWidth = stroke,
        )
    }
}

@Composable
private fun LocationSearchResults(
    results: List<Location>,
    locations: List<Location>,
    selectedLocationId: Long?,
    onLocationSelected: (Location) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 64.dp
    if (results.isEmpty()) {
        Text(
            text = "검색 결과가 없습니다.",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 13.sp,
            modifier = modifier.padding(horizontal = 4.dp, vertical = 14.dp),
        )
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = itemHeight * 3)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(18.dp)),
    ) {
        items(results, key = Location::id) { location ->
            val selected = location.id == selectedLocationId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(
                        if (selected) {
                            TripRecordPalette.current.primarySoft
                        } else {
                            TripRecordPalette.current.surface
                        },
                    )
                    .clickable { onLocationSelected(location) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (location.countryId == KoreaCountryId) "국내" else "해외",
                    color = TripRecordPalette.current.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = location.flowDisplayName(locations),
                    color = TripRecordPalette.current.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
                Text(
                    text = if (selected) "✓" else "›",
                    color = if (selected) {
                        TripRecordPalette.current.accent
                    } else {
                        TripRecordPalette.current.muted
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PhotoLoadingStep(
    locationName: String,
    progress: PhotoLoadingProgress?,
    isPreparingPreviews: Boolean,
    message: String?,
    onBackClick: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(title = "사진 불러오기", onBackClick = onBackClick)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = TripRecordPalette.current.accent,
                trackColor = TripRecordPalette.current.primarySoft,
                strokeWidth = 5.dp,
            )
            Text(
                text = "PHOTO LIBRARY",
                color = TripRecordPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 28.dp),
            )
            Text(
                text = "사진을 정리하고 있어요",
                color = TripRecordPalette.current.headingText,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = if (isPreparingPreviews) {
                    "사진 미리보기 준비 중"
                } else {
                    progress?.let { "${it.processed} / ${it.total}장" }
                        ?: "사진첩을 살펴보는 중"
                },
                color = TripRecordPalette.current.accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 26.dp),
            )
            val percentage = progress?.percentage
            PhotoLoadingBar(
                percentage = percentage,
                indeterminate = isPreparingPreviews,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = when {
                    isPreparingPreviews -> "첫 사진 화면을 준비하고 있어요"
                    percentage != null -> "$percentage%"
                    else -> "진행률 계산 중"
                },
                color = TripRecordPalette.current.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = if (isPreparingPreviews) {
                    "$locationName 사진의 미리보기를 만드는 중입니다."
                } else {
                    "$locationName 사진을 불러오는 중입니다."
                },
                color = TripRecordPalette.current.secondaryText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            message?.let {
                Text(
                    text = it,
                    color = TripRecordPalette.current.danger,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowSmallButton("다시 찾기", onRetry)
                    FlowSmallButton("사진첩에서 선택", onPickFromGallery)
                }
            }
        }
    }
}

@Composable
private fun PhotoLoadingBar(
    percentage: Int?,
    indeterminate: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    val barModifier = modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(8.dp))
    if (indeterminate || percentage == null) {
        LinearProgressIndicator(
            modifier = barModifier,
            color = palette.primary,
            trackColor = palette.primarySoft,
        )
    } else {
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = barModifier,
            color = palette.primary,
            trackColor = palette.primarySoft,
        )
    }
}

@Composable
private fun FlowSmallButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = TripRecordPalette.current.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TripRecordPalette.current.primarySoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun FlowToast(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        color = TripRecordPalette.current.contentOnMedia,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(TripRecordPalette.current.mediaScrim, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 13.dp),
    )
}

@Composable
private fun PhotoPickerStep(
    locationName: String,
    pagingState: PhotoRecommendationPagingState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    message: String?,
    isLoadingMore: Boolean,
    isPreparing: Boolean,
    onBackClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPhotoPreview: (SelectedPhoto) -> Unit,
    onPhotoToggle: (SelectedPhoto) -> Unit,
    onPhotoSelectionChange: (photoId: String, selected: Boolean) -> Unit,
    onGroupToggle: (List<String>) -> Unit,
    onAllToggle: () -> Unit,
) {
    val groups = remember(pagingState.photos) { pagingState.photos.toPhotoDateGroups() }
    val latestSelectedIds by rememberUpdatedState(pagingState.selectedIds)
    val latestSelectionChange by rememberUpdatedState(onPhotoSelectionChange)
    val dragSelectionController = remember {
        PhotoDragSelectionController(
            selectedIds = { latestSelectedIds },
            onSelectionChanged = { photoId, selected ->
                latestSelectionChange(photoId, selected)
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(
            title = "사진 고르기",
            onBackClick = onBackClick,
            actionLabel = if (isPreparing) "저장 요청 중" else "기록하기",
            actionEnabled = pagingState.selectedIds.isNotEmpty() && !isPreparing,
            onActionClick = onCompleteClick,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .navigationBarsPadding()
                .clipToBounds(),
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                PhotoPickerHeader(
                    locationName = locationName,
                    selectedCount = pagingState.selectedIds.size,
                    allSelected = pagingState.photos.isNotEmpty() &&
                        pagingState.photos.all { it.id in pagingState.selectedIds },
                    onAllToggle = onAllToggle,
                    onPickFromGallery = onPickFromGallery,
                )
            }
            message?.let { currentMessage ->
                item {
                    Text(
                        text = currentMessage,
                        color = if (pagingState.photos.isEmpty()) {
                            TripRecordPalette.current.secondaryText
                        } else {
                            TripRecordPalette.current.danger
                        },
                        fontSize = 12.sp,
                    )
                }
            }
            if (groups.isEmpty()) {
                item {
                    EmptyPhotoPicker(onPickFromGallery = onPickFromGallery)
                }
            } else {
                items(groups, key = { it.first }) { (date, photos) ->
                    PhotoDateGroup(
                        date = date,
                        photos = photos,
                        selectedIds = pagingState.selectedIds,
                        onGroupToggle = { onGroupToggle(photos.map(SelectedPhoto::id)) },
                        onPhotoPreview = onPhotoPreview,
                        onPhotoToggle = onPhotoToggle,
                        dragSelectionController = dragSelectionController,
                    )
                }
            }
            if (isLoadingMore) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TripRecordPalette.current.accent,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "사진을 더 불러오는 중…",
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPickerHeader(
    locationName: String,
    selectedCount: Int,
    allSelected: Boolean,
    onAllToggle: () -> Unit,
    onPickFromGallery: () -> Unit,
) {
    Column {
        Text(
            text = "PHOTO LIBRARY",
            color = TripRecordPalette.current.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "$locationName 사진 고르기",
                color = TripRecordPalette.current.headingText,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${selectedCount}장 선택",
                color = TripRecordPalette.current.accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "사진을 누르면 크게 보고, 길게 누른 채 드래그하면 여러 장을 선택할 수 있어요.",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .background(TripRecordPalette.current.surfaceElevated, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "필요한 순간만 골라보세요.",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (allSelected) "모두 해제" else "모두 선택",
                color = TripRecordPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAllToggle)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Text(
                text = "사진첩",
                color = TripRecordPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onPickFromGallery)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyPhotoPicker(onPickFromGallery: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TripRecordPalette.current.surfaceElevated, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "이 장소에서 찾은 사진이 없어요",
            color = TripRecordPalette.current.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "사진첩에서 여행 사진을 직접 선택할 수 있어요.",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "사진첩에서 선택",
            color = TripRecordPalette.current.onPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 18.dp)
                .background(TripRecordPalette.current.primary, RoundedCornerShape(12.dp))
                .clickable(onClick = onPickFromGallery)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PhotoDateGroup(
    date: String,
    photos: List<SelectedPhoto>,
    selectedIds: Set<String>,
    onGroupToggle: () -> Unit,
    onPhotoPreview: (SelectedPhoto) -> Unit,
    onPhotoToggle: (SelectedPhoto) -> Unit,
    dragSelectionController: PhotoDragSelectionController,
) {
    val allSelected = photos.all { it.id in selectedIds }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = date.toFlowDateDisplay(),
                color = TripRecordPalette.current.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${photos.size}장",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (allSelected) "선택 해제" else "전체 선택",
                color = TripRecordPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(TripRecordPalette.current.primarySoft, RoundedCornerShape(10.dp))
                    .clickable(onClick = onGroupToggle)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            photos.chunked(2).forEach { rowPhotos ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowPhotos.forEach { photo ->
                        PhotoSelectionCard(
                            photo = photo,
                            selected = photo.id in selectedIds,
                            onPreview = { onPhotoPreview(photo) },
                            onToggle = { onPhotoToggle(photo) },
                            dragSelectionController = dragSelectionController,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowPhotos.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PhotoSelectionCard(
    photo: SelectedPhoto,
    selected: Boolean,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
    dragSelectionController: PhotoDragSelectionController,
    modifier: Modifier = Modifier,
) {
    var coordinates by remember(photo.id) { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(photo.id, dragSelectionController) {
        onDispose { dragSelectionController.remove(photo.id) }
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) {
                    Modifier.border(3.dp, TripRecordPalette.current.accent, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .onGloballyPositioned { layoutCoordinates ->
                coordinates = layoutCoordinates
                dragSelectionController.updateBounds(
                    photoId = photo.id,
                    bounds = layoutCoordinates.boundsInRoot(),
                )
            }
            .pointerInput(photo.id, dragSelectionController) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        coordinates
                            ?.localToRoot(offset)
                            ?.let { position -> dragSelectionController.start(photo.id, position) }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        coordinates
                            ?.localToRoot(change.position)
                            ?.let(dragSelectionController::moveTo)
                    },
                    onDragEnd = dragSelectionController::finish,
                    onDragCancel = dragSelectionController::finish,
                )
            }
            .clickable(onClick = onPreview)
            .semantics {
                contentDescription = if (selected) {
                    "${photo.displayName}, 선택됨. 길게 누르고 드래그해 연속 선택 해제"
                } else {
                    "${photo.displayName}, 선택 안 됨. 길게 누르고 드래그해 연속 선택"
                }
            }
            .testTag("new-record-photo-${photo.id}"),
    ) {
        TripPhotoImage(
            imageBytes = photo.previewBytes,
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            placeholderVariant = photo.id.hashCode(),
        )
        Text(
            text = if (selected) "✓" else "+",
            color = TripRecordPalette.current.contentOnMedia,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(38.dp)
                .background(
                    if (selected) {
                        TripRecordPalette.current.accent
                    } else {
                        TripRecordPalette.current.mediaScrim
                    },
                    CircleShape,
                )
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun PhotoPreviewDialog(
    photo: SelectedPhoto,
    selected: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TripRecordPalette.current.mediaScrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(TripRecordPalette.current.surface.copy(alpha = 0.8f))
                    .clickable(onClick = {}),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                ) {
                    TripPhotoImage(
                        imageBytes = photo.previewBytes,
                        contentDescription = "${photo.displayName} 확대 사진",
                        modifier = Modifier.fillMaxSize(),
                        placeholderVariant = photo.id.hashCode(),
                    )
                    Text(
                        text = "×",
                        color = TripRecordPalette.current.contentOnMedia,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(42.dp)
                            .background(TripRecordPalette.current.mediaScrim, CircleShape)
                            .clickable(onClick = onDismiss)
                            .padding(top = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = photo.capturedAt?.toFlowDateDisplay() ?: "촬영일 미상",
                        color = TripRecordPalette.current.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (selected) "선택 해제" else "앨범에 추가",
                        color = if (selected) {
                            TripRecordPalette.current.accent
                        } else {
                            TripRecordPalette.current.onPrimary
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                if (selected) {
                                    TripRecordPalette.current.primarySoft
                                } else {
                                    TripRecordPalette.current.primary
                                },
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(onClick = onToggle)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private fun Location.flowDisplayName(locations: List<Location>): String {
    if (type != LocationType.DISTRICT) return name
    val parentName = locations.firstOrNull { it.id == parentId }?.name ?: return name
    return "$parentName $name"
}

private fun String.toFlowDateDisplay(): String {
    if (isBlank()) return "날짜 선택"
    val normalized = replace('-', '.').trimEnd('.')
    return normalized.split('.')
        .filter(String::isNotBlank)
        .joinToString(". ") + "."
}

private fun List<SelectedPhoto>.toPhotoDateGroups(): List<Pair<String, List<SelectedPhoto>>> =
    groupBy { photo -> photo.capturedAt ?: "촬영일 미상" }
        .entries
        .sortedByDescending { entry ->
            entry.key.takeUnless { it == "촬영일 미상" }.orEmpty()
        }
        .map { entry -> entry.key to entry.value }

private fun PhotoRecommendationPagingState.toggleGroup(
    photoIds: List<String>,
): PhotoRecommendationPagingState {
    val availableIds = photoIds.filter { id -> photos.any { it.id == id } }.distinct()
    if (availableIds.isEmpty()) return this
    val allSelected = availableIds.all(selectedIds::contains)
    if (allSelected) return copy(selectedIds = selectedIds - availableIds.toSet())

    val remainingSlots = (maxSelectionCount - selectedIds.size).coerceAtLeast(0)
    val idsToAdd = availableIds
        .filterNot(selectedIds::contains)
        .take(remainingSlots)
    return copy(selectedIds = selectedIds + idsToAdd)
}
