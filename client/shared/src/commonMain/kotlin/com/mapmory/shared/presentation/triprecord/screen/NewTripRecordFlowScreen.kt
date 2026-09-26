package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
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
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPhotoRules
import com.mapmory.shared.domain.model.dateValidationError
import com.mapmory.shared.presentation.date.PlatformDatePicker
import com.mapmory.shared.presentation.map.ui.WorldGlobe
import com.mapmory.shared.presentation.photo.PhotoLibraryActionsFactory
import com.mapmory.shared.presentation.photo.PhotoLibraryPermissionIssue
import com.mapmory.shared.presentation.photo.PhotoLoadingProgress
import com.mapmory.shared.presentation.photo.PhotoRecommendationPagingState
import com.mapmory.shared.presentation.photo.RecommendationLoadKey
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.photo.accept
import com.mapmory.shared.presentation.photo.rememberPhotoLibraryActions
import com.mapmory.shared.presentation.photo.photoRecommendationDateRange
import com.mapmory.shared.presentation.photo.shouldLoadNextRecommendationPage
import com.mapmory.shared.presentation.photo.toggleSelection
import com.mapmory.shared.presentation.triprecord.endDatePickerMinimumDate
import com.mapmory.shared.presentation.triprecord.initialSelectableTripRecordDate
import com.mapmory.shared.presentation.triprecord.selectableTripRecordDestinations
import com.mapmory.shared.presentation.triprecord.startDatePickerMaximumDate
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private enum class NewRecordFlowStep {
    DATE_AND_LOCATION,
    PHOTO_LOADING,
    PHOTO_PICKER,
    ALBUM_DETAILS,
}

private const val PhotoListPrefetchGroups = 2
private const val KoreaCountryId = 1L
private const val PhotoLimitMessageDurationMillis = 3_000L
private const val FlowStartDatePickerTarget = "flow-start"
private const val FlowEndDatePickerTarget = "flow-end"

@Composable
internal fun NewTripRecordFlowScreen(
    uiState: TripRecordEditorUiState,
    locations: List<Location>,
    onLocationSelected: (Location) -> Unit,
    onLocationCleared: () -> Unit,
    onLocationTouched: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
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
    val selectableLocations = remember(locations) {
        locations.selectableTripRecordDestinations()
    }
    var stepName by rememberSaveable { mutableStateOf(NewRecordFlowStep.DATE_AND_LOCATION.name) }
    val step = NewRecordFlowStep.valueOf(stepName)
    var locationSearchQuery by rememberSaveable { mutableStateOf("") }
    var detailsErrorMessage by remember { mutableStateOf<String?>(null) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    var transientPhotoMessage by remember { mutableStateOf<String?>(null) }
    var transientPhotoMessageVersion by remember { mutableStateOf(0) }
    var photoLoadingProgress by remember { mutableStateOf<PhotoLoadingProgress?>(null) }
    var isPreparingPhotoPreviews by remember { mutableStateOf(false) }
    var isRecommendationLoading by remember { mutableStateOf(false) }
    var isPreparingPhotos by remember { mutableStateOf(false) }
    var photoPreparationGeneration by remember { mutableStateOf(0) }
    var photoPermissionIssue by remember { mutableStateOf<PhotoLibraryPermissionIssue?>(null) }
    var recommendationPagingState by remember {
        mutableStateOf(PhotoRecommendationPagingState())
    }
    var lastAutoLoadTriggerKey by remember { mutableStateOf<RecommendationLoadKey?>(null) }
    var previewPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var datePickerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val photoListState = rememberLazyListState()

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
                replaceEditorPhotos(acceptedPhotos)
                photoMessage = null
                stepName = NewRecordFlowStep.ALBUM_DETAILS.name
            }
        },
        { page ->
            recommendationPagingState
                .accept(page, autoSelectNewPhotos = false)
                ?.let { nextState ->
                    recommendationPagingState = nextState
                    if (step == NewRecordFlowStep.PHOTO_LOADING) {
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
            if (!isLoading && isPreparingPhotos) isPreparingPhotos = false
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
        when (step) {
            NewRecordFlowStep.DATE_AND_LOCATION -> onBackClick()
            NewRecordFlowStep.PHOTO_LOADING -> {
                photoLibrary.cancelRecommendation()
                onPhotoLoadingChanged(false)
                isPreparingPhotoPreviews = false
                stepName = NewRecordFlowStep.DATE_AND_LOCATION.name
            }
            NewRecordFlowStep.PHOTO_PICKER -> {
                photoLibrary.cancelRecommendation()
                photoPreparationGeneration += 1
                isPreparingPhotos = false
                onPhotoLoadingChanged(false)
                stepName = NewRecordFlowStep.DATE_AND_LOCATION.name
            }
            NewRecordFlowStep.ALBUM_DETAILS -> {
                stepName = if (recommendationPagingState.photos.isEmpty()) {
                    NewRecordFlowStep.DATE_AND_LOCATION.name
                } else {
                    NewRecordFlowStep.PHOTO_PICKER.name
                }
            }
        }
    }

    val latestInternalBackHandler by rememberUpdatedState {
        returnToPreviousStep()
        true
    }
    DisposableEffect(step) {
        onInternalBackHandlerChanged(
            if (step == NewRecordFlowStep.DATE_AND_LOCATION) null else latestInternalBackHandler,
        )
        onDispose { onInternalBackHandlerChanged(null) }
    }

    fun beginPhotoSearch() {
        val location = uiState.selectedLocation
        val dateError = location?.let {
            TripRecordDraft(
                locationId = it.id,
                startDate = uiState.startDate,
                endDate = uiState.endDate.ifBlank { null },
                mediaObjectKeys = emptyList(),
            ).dateValidationError()
        }
        when {
            location == null -> {
                onLocationTouched()
                detailsErrorMessage = "장소를 선택해 주세요."
            }
            uiState.startDate.isBlank() -> {
                detailsErrorMessage = "시작일을 선택해 주세요."
            }
            dateError != null -> {
                detailsErrorMessage = dateError
            }
            !photoLibrary.recommendationsAvailable -> {
                recommendationPagingState = PhotoRecommendationPagingState()
                photoMessage = "이 기기에서는 위치로 사진을 찾을 수 없어요. 사진첩에서 직접 골라주세요."
                stepName = NewRecordFlowStep.PHOTO_PICKER.name
            }
            else -> {
                detailsErrorMessage = null
                photoMessage = null
                photoLoadingProgress = null
                isPreparingPhotoPreviews = false
                recommendationPagingState = PhotoRecommendationPagingState()
                lastAutoLoadTriggerKey = null
                stepName = NewRecordFlowStep.PHOTO_LOADING.name
                val parentName = locations.firstOrNull { it.id == location.parentId }?.name
                val dateRange = requireNotNull(
                    photoRecommendationDateRange(uiState.startDate, uiState.endDate),
                )
                photoLibrary.recommendForLocationInDateRange(location, parentName, dateRange)
            }
        }
    }

    fun completePhotoSelection() {
        val selectedPhotos = recommendationPagingState.photos.filter { photo ->
            photo.id in recommendationPagingState.selectedIds
        }
        if (selectedPhotos.isEmpty()) {
            photoMessage = "앨범에 넣을 사진을 한 장 이상 선택해 주세요."
            return
        }
        val preparationGeneration = photoPreparationGeneration + 1
        photoPreparationGeneration = preparationGeneration
        isPreparingPhotos = true
        onPhotoLoadingChanged(true)
        photoLibrary.prepareForAdding(selectedPhotos) { preparedPhotos ->
            if (
                preparationGeneration != photoPreparationGeneration ||
                stepName != NewRecordFlowStep.PHOTO_PICKER.name
            ) {
                return@prepareForAdding
            }
            isPreparingPhotos = false
            onPhotoLoadingChanged(false)
            if (preparedPhotos.isEmpty()) {
                photoMessage = "선택한 사진의 원본을 읽지 못했어요."
            } else {
                replaceEditorPhotos(preparedPhotos)
                photoMessage = null
                stepName = NewRecordFlowStep.ALBUM_DETAILS.name
            }
        }
    }

    TripRecordBackground(
        modifier = modifier,
        backgroundColor = TripRecordPalette.current.pageBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            when (step) {
            NewRecordFlowStep.DATE_AND_LOCATION -> DateAndLocationStep(
                uiState = uiState,
                locations = locations,
                searchQuery = locationSearchQuery,
                searchResults = filteredLocations,
                errorMessage = detailsErrorMessage,
                onSearchQueryChanged = {
                    locationSearchQuery = it
                    val selectedName = uiState.selectedLocation?.flowDisplayName(locations)
                    if (selectedName != null && it != selectedName) onLocationCleared()
                    detailsErrorMessage = null
                },
                onLocationSelected = { location ->
                    onLocationSelected(location)
                    locationSearchQuery = location.flowDisplayName(locations)
                    detailsErrorMessage = null
                },
                onStartDateClick = { datePickerTarget = FlowStartDatePickerTarget },
                onEndDateClick = { datePickerTarget = FlowEndDatePickerTarget },
                onGlobeCountryClick = { countryCode ->
                    val location = selectableLocations.firstOrNull { candidate ->
                        candidate.regionCode == countryCode
                    }
                    if (location == null) {
                        detailsErrorMessage = if (countryCode == "KR") {
                            "국내 여행은 위 검색창에서 시·군·구를 선택해 주세요."
                        } else {
                            "선택한 나라는 아직 지원하지 않아요."
                        }
                    } else {
                        onLocationSelected(location)
                        locationSearchQuery = location.flowDisplayName(locations)
                        detailsErrorMessage = null
                    }
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
                onPickFromGallery = photoLibrary.pickFromGallery,
                onRetry = ::beginPhotoSearch,
            )

            NewRecordFlowStep.PHOTO_PICKER -> PhotoPickerStep(
                locationName = uiState.selectedLocation?.name ?: "여행지",
                pagingState = recommendationPagingState,
                listState = photoListState,
                message = photoMessage,
                isLoadingMore = isRecommendationLoading,
                isPreparing = isPreparingPhotos,
                onBackClick = ::returnToPreviousStep,
                onCompleteClick = ::completePhotoSelection,
                onPickFromGallery = photoLibrary.pickFromGallery,
                onPhotoPreview = { previewPhotoId = it.id },
                onPhotoToggle = { photo ->
                    val next = recommendationPagingState.toggleSelection(photo.id)
                    if (next == recommendationPagingState && photo.id !in next.selectedIds) {
                        previewPhotoId = null
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

            NewRecordFlowStep.ALBUM_DETAILS -> AlbumDetailsStep(
                uiState = uiState,
                onTitleChanged = onTitleChanged,
                onContentChanged = onContentChanged,
                onBackClick = ::returnToPreviousStep,
                onSaveClick = onSaveClick,
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

    val activeDatePickerTarget = datePickerTarget
    val today = remember(activeDatePickerTarget) {
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
    }
    val minimumDate = if (activeDatePickerTarget == FlowEndDatePickerTarget) {
        endDatePickerMinimumDate(uiState.startDate, today)
    } else {
        null
    }
    val maximumDate = if (activeDatePickerTarget == FlowStartDatePickerTarget) {
        startDatePickerMaximumDate(uiState.endDate, today)
    } else {
        today
    }
    val selectedDate = when (activeDatePickerTarget) {
        FlowStartDatePickerTarget -> uiState.startDate
        FlowEndDatePickerTarget -> uiState.endDate
        else -> null
    }
    PlatformDatePicker(
        visible = activeDatePickerTarget != null,
        initialDate = initialSelectableTripRecordDate(
            selectedDate = selectedDate,
            fallbackDate = today,
            minimumDate = minimumDate,
            maximumDate = maximumDate,
        ),
        minimumDate = minimumDate,
        maximumDate = maximumDate,
        onDateSelected = { date ->
            if (activeDatePickerTarget == FlowStartDatePickerTarget) {
                onStartDateChanged(date)
            } else if (activeDatePickerTarget == FlowEndDatePickerTarget) {
                onEndDateChanged(date)
            }
            detailsErrorMessage = null
            datePickerTarget = null
        },
        onDismiss = { datePickerTarget = null },
    )
}

@Composable
private fun DateAndLocationStep(
    uiState: TripRecordEditorUiState,
    locations: List<Location>,
    searchQuery: String,
    searchResults: List<Location>,
    errorMessage: String?,
    onSearchQueryChanged: (String) -> Unit,
    onLocationSelected: (Location) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onGlobeCountryClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onCompleteClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(
            title = "일자, 장소 선택",
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
                text = "여행의 시작을 알려주세요",
                color = TripRecordPalette.current.headingText,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = "장소와 날짜를 고르면 사진첩에서 해당 여행의 사진을 찾아드려요.",
                color = TripRecordPalette.current.bodyText,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            FlowSectionTitle(
                title = "여행 일자",
                badge = "필수",
                helper = "시작일을 선택해 주세요.",
                modifier = Modifier.padding(top = 34.dp),
            )
            DateRangePicker(
                startDate = uiState.startDate,
                endDate = uiState.endDate,
                onStartDateClick = onStartDateClick,
                onEndDateClick = onEndDateClick,
                modifier = Modifier.padding(top = 12.dp),
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
                text = "지도에서 직접 선택",
                color = TripRecordPalette.current.headingText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 30.dp),
            )
            Text(
                text = "지구본을 돌려 원하는 나라를 탭해보세요.",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(360.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(24.dp)),
            ) {
                WorldGlobe(
                    visitedCountryCodes = uiState.selectedLocation
                        ?.regionCode
                        ?.takeIf { uiState.selectedLocation.countryId != KoreaCountryId }
                        ?.let(::setOf)
                        .orEmpty(),
                    onCountryClick = onGlobeCountryClick,
                )
                Text(
                    text = uiState.selectedLocation?.flowDisplayName(locations)
                        ?: "나라를 선택해보세요!",
                    color = TripRecordPalette.current.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .background(
                            TripRecordPalette.current.surface.copy(alpha = 0.88f),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
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
private fun DateRangePicker(
    startDate: String,
    endDate: String,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TripRecordPalette.current.surfaceElevated, RoundedCornerShape(18.dp))
            .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(18.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateRangeCell(
            label = "시작일",
            value = startDate,
            onClick = onStartDateClick,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "—",
            color = TripRecordPalette.current.muted,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        DateRangeCell(
            label = "종료일 (선택)",
            value = endDate,
            onClick = onEndDateClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DateRangeCell(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
    ) {
        Text(
            text = label,
            color = TripRecordPalette.current.secondaryText,
            fontSize = 10.sp,
        )
        Text(
            text = value.toFlowDateDisplay(),
            color = if (value.isBlank()) {
                TripRecordPalette.current.muted
            } else {
                TripRecordPalette.current.text
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp),
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
    onGroupToggle: (List<String>) -> Unit,
    onAllToggle: () -> Unit,
) {
    val groups = remember(pagingState.photos) { pagingState.photos.toPhotoDateGroups() }
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(
            title = "사진 고르기",
            onBackClick = onBackClick,
            actionLabel = if (isPreparing) "준비 중" else "완료",
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
            text = "최신순으로 정리했어요. 사진을 누르면 크게 볼 수 있어요.",
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
    modifier: Modifier = Modifier,
) {
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
            .clickable(onClick = onPreview)
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

@Composable
private fun AlbumDetailsStep(
    uiState: TripRecordEditorUiState,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(
            title = "새 앨범 완성하기",
            onBackClick = onBackClick,
            actionLabel = if (uiState.isSaving) "저장 중" else "완료",
            actionEnabled = uiState.isSaveEnabled,
            onActionClick = onSaveClick,
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
                text = "ALBUM PREVIEW",
                color = TripRecordPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = "새 앨범을 완성해요",
                color = TripRecordPalette.current.headingText,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = "${uiState.selectedLocation?.name ?: "여행지"}에서 고른 " +
                    "${uiState.selectedPhotos.size}장의 사진이에요.",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                uiState.selectedPhotos.forEach { photo ->
                    AlbumPreviewPhoto(photo)
                }
            }
            AlbumTextField(
                label = "앨범 제목",
                helper = "선택",
                value = uiState.title,
                placeholder = "여행 제목을 입력해 주세요",
                singleLine = true,
                onValueChange = onTitleChanged,
                modifier = Modifier.padding(top = 32.dp),
            )
            AlbumTextField(
                label = "대표 캡션",
                helper = "선택",
                value = uiState.content,
                placeholder = "이번 여행을 한 문장으로 남겨보세요.",
                singleLine = false,
                onValueChange = onContentChanged,
                modifier = Modifier.padding(top = 24.dp),
            )
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = TripRecordPalette.current.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumPreviewPhoto(photo: TripRecordPhotoUiState) {
    TripPhotoImage(
        imageBytes = photo.previewBytes?.bytesForDecoding(),
        fallbackBytes = photo.originalBytes?.bytesForDecoding(),
        contentDescription = photo.displayName,
        modifier = Modifier.size(width = 146.dp, height = 190.dp),
        placeholderVariant = photo.id.hashCode(),
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun AlbumTextField(
    label: String,
    helper: String,
    value: String,
    placeholder: String,
    singleLine: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TripRecordPalette.current.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = helper,
            color = TripRecordPalette.current.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 4,
            maxLines = if (singleLine) 1 else 6,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TripRecordPalette.current.text,
                unfocusedTextColor = TripRecordPalette.current.text,
                cursorColor = TripRecordPalette.current.accent,
                focusedBorderColor = TripRecordPalette.current.accent,
                unfocusedBorderColor = TripRecordPalette.current.border,
                focusedContainerColor = TripRecordPalette.current.surfaceElevated,
                unfocusedContainerColor = TripRecordPalette.current.surfaceElevated,
                focusedPlaceholderColor = TripRecordPalette.current.muted,
                unfocusedPlaceholderColor = TripRecordPalette.current.muted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
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
