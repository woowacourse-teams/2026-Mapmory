package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.components.MapmoryPhotoExpansion
import com.mapmory.shared.presentation.components.MapmoryPhotoViewer
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mapmory.shared.presentation.components.LocalMapmoryImageTransitionScope
import androidx.compose.ui.unit.sp
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceReference
import com.mapmory.shared.domain.model.TripRecordPhotoRules
import com.mapmory.shared.presentation.photo.PhotoLibraryActionsFactory
import com.mapmory.shared.presentation.photo.PhotoLibraryPermissionIssue
import com.mapmory.shared.presentation.photo.PhotoLoadingProgress
import com.mapmory.shared.presentation.photo.PhotoRecommendationPagingState
import com.mapmory.shared.presentation.photo.RecommendationLoadKey
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.photo.accept
import com.mapmory.shared.presentation.photo.photoRecommendationDateRange
import com.mapmory.shared.presentation.photo.rememberPhotoLibraryActions
import com.mapmory.shared.presentation.photo.selectAllLoadedPhotos
import com.mapmory.shared.presentation.photo.setSelection
import com.mapmory.shared.presentation.photo.shouldLoadNextRecommendationPage
import com.mapmory.shared.presentation.photo.toggleSelection
import com.mapmory.shared.presentation.triprecord.selectableTripRecordDestinations
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class NewRecordFlowStep {
    LOCATION,
    PHOTO_LOADING,
    PHOTO_PICKER,
}

internal enum class NewRecordBackAction {
    IGNORE,
    CLOSE_PREVIEW,
    CONFIRM_PHOTO_LOADING,
    CONFIRM_PHOTO_PICKER,
    EXIT_FLOW,
}

internal fun newRecordBackAction(
    step: NewRecordFlowStep,
    isSaving: Boolean,
    hasOpenPhotoPreview: Boolean,
    isPhotoLoading: Boolean,
): NewRecordBackAction = when {
    isSaving -> NewRecordBackAction.IGNORE
    hasOpenPhotoPreview -> NewRecordBackAction.CLOSE_PREVIEW
    step == NewRecordFlowStep.PHOTO_LOADING && isPhotoLoading -> {
        NewRecordBackAction.CONFIRM_PHOTO_LOADING
    }
    step == NewRecordFlowStep.PHOTO_PICKER -> NewRecordBackAction.CONFIRM_PHOTO_PICKER
    else -> NewRecordBackAction.EXIT_FLOW
}

private const val KoreaCountryId = 1L
private const val MinPlaceQueryLength = 2
private const val MaxPlaceQueryLength = 100
private const val PlaceSearchDebounceMillis = 350L
private const val PhotoListPrefetchItems = 4
private const val PhotoLimitMessageDurationMillis = 3_000L
private const val DragAutoScrollFrameMillis = 16L
private const val DefaultPhotoGridColumns = 2
private const val MinPhotoGridColumns = 1
private const val MaxPhotoGridColumns = 4
private const val PhotoGridZoomThreshold = 1.25f

internal data class PhotoGridPinchResult(
    val columnCount: Int,
    val accumulatedZoom: Float,
)

internal fun photoGridPinchResultAfterZoom(current: Int, accumulatedZoom: Float): PhotoGridPinchResult? {
    val nextColumns = when {
        accumulatedZoom >= PhotoGridZoomThreshold -> (current - 1).coerceAtLeast(MinPhotoGridColumns)
        accumulatedZoom <= 1f / PhotoGridZoomThreshold -> (current + 1).coerceAtMost(MaxPhotoGridColumns)
        else -> return null
    }
    val columnsChanged = nextColumns != current

    return PhotoGridPinchResult(
        columnCount = nextColumns,
        accumulatedZoom = when {
            !columnsChanged -> 1f
            accumulatedZoom >= PhotoGridZoomThreshold -> accumulatedZoom / PhotoGridZoomThreshold
            else -> accumulatedZoom * PhotoGridZoomThreshold
        },
    )
}

internal data class PhotoPickerScrollBarMetrics(
    val thumbFraction: Float,
    val scrollFraction: Float,
)

internal data class PhotoPickerScrollTarget(
    val itemIndex: Int,
    val itemScrollOffset: Int,
)

internal fun photoPickerScrollTarget(
    scrollFraction: Float,
    viewportSize: Int,
    itemSizes: List<Int>,
    itemSpacing: Int,
    contentPaddingBefore: Int,
    contentPaddingAfter: Int,
): PhotoPickerScrollTarget? {
    if (viewportSize <= 0 || itemSizes.isEmpty()) return null
    val contentSize = contentPaddingBefore + contentPaddingAfter +
        itemSizes.sum() + itemSpacing * (itemSizes.size - 1).coerceAtLeast(0)
    val maximumScrollOffset = (contentSize - viewportSize).coerceAtLeast(0)
    var remainingOffset =
        (scrollFraction.coerceIn(0f, 1f) * maximumScrollOffset).roundToInt() - contentPaddingBefore
    if (remainingOffset <= 0) return PhotoPickerScrollTarget(0, 0)

    itemSizes.forEachIndexed { index, itemSize ->
        if (remainingOffset < itemSize + itemSpacing || index == itemSizes.lastIndex) {
            return PhotoPickerScrollTarget(
                itemIndex = index,
                itemScrollOffset = remainingOffset.coerceIn(0, itemSize),
            )
        }
        remainingOffset -= itemSize + itemSpacing
    }
    return PhotoPickerScrollTarget(itemSizes.lastIndex, itemSizes.last())
}

internal fun photoPickerScrollBarMetrics(
    scrollOffset: Int,
    viewportSize: Int,
    contentSize: Int,
): PhotoPickerScrollBarMetrics? {
    if (viewportSize <= 0 || contentSize <= viewportSize) return null
    val maxScrollOffset = contentSize - viewportSize
    return PhotoPickerScrollBarMetrics(
        thumbFraction = (viewportSize.toFloat() / contentSize).coerceIn(0f, 1f),
        scrollFraction = (scrollOffset.toFloat() / maxScrollOffset).coerceIn(0f, 1f),
    )
}

internal fun photoDragAutoScrollVelocity(
    pointerY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edgeSize: Float,
    maximumStep: Float,
): Float {
    if (edgeSize <= 0f || viewportBottom <= viewportTop) return 0f
    return when {
        pointerY < viewportTop + edgeSize -> {
            val proximity = ((viewportTop + edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
            -maximumStep * proximity * proximity
        }
        pointerY > viewportBottom - edgeSize -> {
            val proximity = ((pointerY - (viewportBottom - edgeSize)) / edgeSize).coerceIn(0f, 1f)
            maximumStep * proximity * proximity
        }
        else -> 0f
    }
}

internal class PhotoDragSelectionController(
    private val selectedIds: () -> Set<String>,
    private val onSelectionChanged: (photoId: String, selected: Boolean) -> Unit,
    private val orderedIds: () -> List<String> = { emptyList() },
    private val maxSelectionCount: Int = TripRecordPhotoRules.MaxPhotosPerRecord,
) {
    private val boundsByPhotoId = mutableMapOf<String, Rect>()
    private var initialSelectedIds = emptySet<String>()
    private var currentSelectedIds = mutableSetOf<String>()
    private var anchorId: String? = null
    private var selectionValue: Boolean? = null

    fun photoAt(position: Offset): String? = boundsByPhotoId.entries
        .firstOrNull { (_, bounds) -> bounds.contains(position) }?.key

    fun updateBounds(photoId: String, bounds: Rect) {
        boundsByPhotoId[photoId] = bounds
    }

    fun remove(photoId: String) {
        boundsByPhotoId.remove(photoId)
    }

    fun start(photoId: String, positionInRoot: Offset) {
        initialSelectedIds = selectedIds().toSet()
        currentSelectedIds = initialSelectedIds.toMutableSet()
        anchorId = photoId
        selectionValue = photoId !in currentSelectedIds
        moveTo(positionInRoot)
    }

    fun moveTo(positionInRoot: Offset) {
        val anchor = anchorId ?: return
        val selected = selectionValue ?: return
        val target = photoAt(positionInRoot) ?: boundsByPhotoId.entries.minByOrNull { (_, bounds) ->
            val dx = positionInRoot.x - positionInRoot.x.coerceIn(bounds.left, bounds.right)
            val dy = positionInRoot.y - positionInRoot.y.coerceIn(bounds.top, bounds.bottom)
            dx * dx + dy * dy
        }?.key ?: return
        val ids = orderedIds().ifEmpty { boundsByPhotoId.keys.toList() }
        val start = ids.indexOf(anchor)
        val end = ids.indexOf(target)
        if (start < 0 || end < 0) return
        val range = if (start <= end) start..end else start downTo end
        val rangeIds = range.map { ids[it] }
        val desired = if (selected) {
            initialSelectedIds + rangeIds.filterNot(initialSelectedIds::contains)
                .take((maxSelectionCount - initialSelectedIds.size).coerceAtLeast(0))
        } else {
            initialSelectedIds - rangeIds.toSet()
        }
        // Restore contracted ranges before adding new items so the limit never blocks restoration.
        (currentSelectedIds - desired).forEach { onSelectionChanged(it, false) }
        (desired - currentSelectedIds).forEach { onSelectionChanged(it, true) }
        currentSelectedIds = desired.toMutableSet()
    }

    fun finish() {
        anchorId = null
        selectionValue = null
        initialSelectedIds = emptySet()
        currentSelectedIds.clear()
    }
}

@Composable
internal fun NewTripRecordFlowScreen(
    uiState: TripRecordEditorUiState,
    locations: List<Location>,
    onLocationSelected: (Location) -> Unit,
    onLocationCleared: () -> Unit,
    onLocationTouched: () -> Unit,
    onPlaceSearchQueryChanged: () -> Unit,
    onSearchPlaces: suspend (String) -> Unit,
    onPlaceCandidateSelected: suspend (PlaceCandidate) -> Location?,
    onSelectedPlaceCleared: () -> Unit,
    onPhotosAdded: (List<SelectedPhoto>) -> Unit,
    onPhotoRemoved: (String) -> Unit,
    onPhotoLoadingChanged: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    onConfirmedPhotoLoadingBackClick: () -> Unit = onBackClick,
    recordedPhotoIds: Set<String> = emptySet(),
    photoUsageReady: Boolean = true,
    startWithPhotoSearch: Boolean = false,
    saveActionLabel: String = "기록하기",
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
    val placeSelectionScope = rememberCoroutineScope()
    val selectableLocations = remember(locations) {
        locations.selectableTripRecordDestinations()
    }
    var stepName by rememberSaveable(startWithPhotoSearch) {
        mutableStateOf(
            if (startWithPhotoSearch) {
                NewRecordFlowStep.PHOTO_LOADING.name
            } else {
                NewRecordFlowStep.LOCATION.name
            },
        )
    }
    val step = NewRecordFlowStep.valueOf(stepName)
    var excludeRecordedPhotos by rememberSaveable { mutableStateOf(true) }
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
    var lastSelectAllLoadTriggerKey by remember { mutableStateOf<RecommendationLoadKey?>(null) }
    var isSelectingAllPhotos by remember { mutableStateOf(false) }
    var isRefreshingRecordedPhotoFilter by remember { mutableStateOf(false) }
    var showPhotoLoadingBackConfirmation by remember { mutableStateOf(false) }
    var showPhotoPickerBackConfirmation by remember { mutableStateOf(false) }
    var previewPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var fullResolutionPreviewBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingLocation by remember { mutableStateOf<Location?>(null) }
    var pendingSave by remember { mutableStateOf(false) }
    var hasStartedInitialPhotoSearch by rememberSaveable(startWithPhotoSearch) { mutableStateOf(false) }
    val photoListState = rememberLazyListState()
    val preselectedLocalPhotoIds = remember(
        startWithPhotoSearch,
        uiState.recordId,
        uiState.selectedPhotos,
    ) {
        if (startWithPhotoSearch) {
            uiState.selectedPhotos
                .asSequence()
                .mapNotNull { photo -> photo.localPhotoId }
                .distinct()
                .take(TripRecordPhotoRules.MaxPhotosPerRecord)
                .toSet()
        } else {
            emptySet()
        }
    }

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

    fun cancelSelectAllContinuation() {
        isSelectingAllPhotos = false
        lastSelectAllLoadTriggerKey = null
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
                if (startWithPhotoSearch) {
                    val current = recommendationPagingState
                    val combined = (current.photos + acceptedPhotos).distinctBy { it.id }
                    recommendationPagingState = current.copy(
                        photos = combined,
                        selectedIds = (current.selectedIds + acceptedPhotos.map { it.id })
                            .take(TripRecordPhotoRules.MaxPhotosPerRecord).toSet(),
                    )
                    if (current.selectedIds.size + acceptedPhotos.count { it.id !in current.selectedIds } >
                        TripRecordPhotoRules.MaxPhotosPerRecord
                    ) showPhotoLimitMessage()
                } else {
                    recommendationPagingState = PhotoRecommendationPagingState(
                        photos = acceptedPhotos,
                        selectedIds = acceptedPhotos.mapTo(mutableSetOf()) { it.id },
                    )
                    replaceEditorPhotos(acceptedPhotos)
                    pendingSave = true
                }
                photoMessage = null
            }
        },
        { page ->
            val pagingStateToUpdate = if (isRefreshingRecordedPhotoFilter) {
                PhotoRecommendationPagingState()
            } else {
                recommendationPagingState
            }
            pagingStateToUpdate
                .accept(
                    page = page,
                    autoSelectNewPhotos = false,
                    preselectedIds = preselectedLocalPhotoIds,
                )
                ?.let { nextState ->
                    recommendationPagingState = if (isSelectingAllPhotos) {
                        nextState.selectAllLoadedPhotos()
                    } else {
                        nextState
                    }
                    isRefreshingRecordedPhotoFilter = false
                    if (stepName == NewRecordFlowStep.PHOTO_LOADING.name) {
                        isPreparingPhotoPreviews = false
                        stepName = NewRecordFlowStep.PHOTO_PICKER.name
                    }
                    if (nextState.photos.isEmpty() && !page.hasMore) {
                        photoMessage = if (page.excludedCount > 0) {
                            "이 장소의 사진을 모두 기록했어요. 제외 옵션을 끄면 다시 선택할 수 있어요."
                        } else {
                            null
                        }
                    } else if (nextState.photos.isNotEmpty()) {
                        photoMessage = null
                    }
                }
        },
        { message ->
            photoMessage = message
            isRefreshingRecordedPhotoFilter = false
        },
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

    LaunchedEffect(previewPhotoId) {
        val photoId = previewPhotoId ?: run {
            fullResolutionPreviewBytes = null
            return@LaunchedEffect
        }
        val photo = recommendationPagingState.photos.firstOrNull { it.id == photoId }
            ?: return@LaunchedEffect
        fullResolutionPreviewBytes = null
        if (photo.fullResolutionUri == null) {
            photoLibrary.loadFullResolutionPreview(photo) { bytes ->
                if (previewPhotoId == photoId) fullResolutionPreviewBytes = bytes
            }
        }
    }

    LaunchedEffect(
        isSelectingAllPhotos,
        recommendationPagingState.generation,
        recommendationPagingState.photos.size,
        recommendationPagingState.selectedIds.size,
        recommendationPagingState.hasMore,
        isRecommendationLoading,
    ) {
        if (!isSelectingAllPhotos) return@LaunchedEffect

        val selectedState = recommendationPagingState.selectAllLoadedPhotos()
        if (selectedState != recommendationPagingState) {
            recommendationPagingState = selectedState
            return@LaunchedEffect
        }
        if (
            selectedState.selectedIds.size >= selectedState.maxSelectionCount ||
            !selectedState.hasMore
        ) {
            isSelectingAllPhotos = false
            lastSelectAllLoadTriggerKey = null
            return@LaunchedEffect
        }
        if (isRecommendationLoading) return@LaunchedEffect

        val generation = selectedState.generation ?: return@LaunchedEffect
        val currentKey = RecommendationLoadKey(generation, selectedState.photos.size)
        if (lastSelectAllLoadTriggerKey != currentKey) {
            lastSelectAllLoadTriggerKey = currentKey
            photoLibrary.loadNextRecommendationPage()
        }
    }

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

    LaunchedEffect(uiState.selectedLocation?.id, uiState.selectedPlace?.placeId) {
        val selectedValue = uiState.selectedPlace?.name
            ?: uiState.selectedLocation?.flowDisplayName(locations)
        if (locationSearchQuery.isBlank() && selectedValue != null) {
            locationSearchQuery = selectedValue
        }
    }

    LaunchedEffect(
        locationSearchQuery,
        uiState.selectedLocation?.id,
        uiState.selectedPlace?.placeId,
        uiState.isSelectingPlace,
        uiState.manualRegionRequired,
    ) {
        val query = locationSearchQuery.trim()
        val selectedRegionName = uiState.selectedLocation?.flowDisplayName(locations)
        if (
            !uiState.isPlaceSearchAvailable ||
            query.length < MinPlaceQueryLength ||
            query.equals(uiState.selectedPlace?.name, ignoreCase = true) ||
            query.equals(selectedRegionName, ignoreCase = true) ||
            uiState.isSelectingPlace ||
            uiState.manualRegionRequired
        ) {
            return@LaunchedEffect
        }
        delay(PlaceSearchDebounceMillis)
        onSearchPlaces(query.take(MaxPlaceQueryLength))
    }

    LaunchedEffect(
        step,
        recommendationPagingState.generation,
        recommendationPagingState.photos.size,
        recommendationPagingState.hasMore,
        isRecommendationLoading,
        isSelectingAllPhotos,
    ) {
        if (
            step != NewRecordFlowStep.PHOTO_PICKER ||
            isRecommendationLoading ||
            isSelectingAllPhotos
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val info = photoListState.layoutInfo
            val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 &&
                lastVisibleIndex >= info.totalItemsCount - PhotoListPrefetchItems
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
        when (
            newRecordBackAction(
                step = step,
                isSaving = uiState.isSaving,
                hasOpenPhotoPreview = previewPhotoId != null,
                isPhotoLoading = uiState.isPhotoLoading ||
                    isRecommendationLoading ||
                    isPreparingPhotoPreviews ||
                    isRefreshingRecordedPhotoFilter,
            )
        ) {
            NewRecordBackAction.IGNORE -> Unit
            NewRecordBackAction.CLOSE_PREVIEW -> previewPhotoId = null
            NewRecordBackAction.CONFIRM_PHOTO_LOADING -> {
                showPhotoLoadingBackConfirmation = true
            }
            NewRecordBackAction.CONFIRM_PHOTO_PICKER -> {
                showPhotoPickerBackConfirmation = true
            }
            NewRecordBackAction.EXIT_FLOW -> {
                if (startWithPhotoSearch || step == NewRecordFlowStep.LOCATION) {
                    onBackClick()
                } else {
                    photoLibrary.cancelRecommendation()
                    isPreparingPhotoPreviews = false
                    isRefreshingRecordedPhotoFilter = false
                    onPhotoLoadingChanged(false)
                    stepName = NewRecordFlowStep.LOCATION.name
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
            if (step == NewRecordFlowStep.LOCATION) null else ({ latestInternalBackHandler() }),
        )
        onDispose {
            onInternalBackHandlerChanged(null)
        }
    }

    fun beginPhotoSearch(
        keepPhotoPickerVisible: Boolean = false,
        shouldExcludeRecordedPhotos: Boolean = excludeRecordedPhotos,
    ) {
        if (!photoUsageReady) {
            detailsErrorMessage = "기록한 사진을 확인하고 있어요. 잠시 후 다시 눌러주세요."
            return
        }
        photoLibrary.setExcludedPhotoIds(
            if (shouldExcludeRecordedPhotos && !startWithPhotoSearch) recordedPhotoIds else emptySet(),
        )
        if (!keepPhotoPickerVisible) {
            val existingPhotos = if (startWithPhotoSearch) {
                uiState.selectedPhotos.map { photo ->
                    SelectedPhoto(
                        id = photo.localPhotoId ?: photo.id,
                        displayName = photo.displayName,
                        previewBytes = photo.previewBytes?.bytesForDecoding(),
                        previewUri = photo.previewUri,
                        fullResolutionUri = photo.fullResolutionUri ?: photo.previewUri,
                        latitude = photo.latitude,
                        longitude = photo.longitude,
                        capturedAt = photo.capturedAt,
                    )
                }
            } else emptyList()
            recommendationPagingState = PhotoRecommendationPagingState(
                photos = existingPhotos,
                selectedIds = existingPhotos.mapTo(mutableSetOf()) { it.id },
            )
        }
        val location = uiState.selectedLocation
        when {
            location == null -> {
                onLocationTouched()
                detailsErrorMessage = "장소를 선택해 주세요."
            }
            !photoLibrary.recommendationsAvailable -> {
                photoMessage = "이 기기에서는 위치로 사진을 찾을 수 없어요. 사진첩에서 직접 골라주세요."
                stepName = NewRecordFlowStep.PHOTO_PICKER.name
            }
            else -> {
                logFieldInteraction("photos")
                detailsErrorMessage = null
                if (!keepPhotoPickerVisible) photoMessage = null
                photoLoadingProgress = null
                isPreparingPhotoPreviews = false
                lastAutoLoadTriggerKey = null
                lastSelectAllLoadTriggerKey = null
                isSelectingAllPhotos = false
                isRefreshingRecordedPhotoFilter = keepPhotoPickerVisible
                if (!keepPhotoPickerVisible) {
                    stepName = NewRecordFlowStep.PHOTO_LOADING.name
                }
                analytics.logEvent(
                    MapmoryAnalyticsEvent.PHOTO_RECOMMENDATION_STARTED,
                    mapOf("location_type" to location.type.name.lowercase()),
                )
                val parentName = locations.firstOrNull { it.id == location.parentId }?.name
                val dateRange = if (startWithPhotoSearch) {
                    photoRecommendationDateRange(uiState.startDate, uiState.endDate)
                } else {
                    null
                }
                if (dateRange == null) {
                    photoLibrary.recommendForLocation(location, parentName)
                } else {
                    photoLibrary.recommendForLocationInDateRange(location, parentName, dateRange)
                }
            }
        }
    }

    LaunchedEffect(pendingLocation, uiState.selectedLocation) {
        if (pendingLocation != null && pendingLocation == uiState.selectedLocation) {
            pendingLocation = null
            beginPhotoSearch()
        }
    }

    LaunchedEffect(startWithPhotoSearch, uiState.recordId, uiState.selectedLocation, photoUsageReady) {
        if (
            startWithPhotoSearch &&
            photoUsageReady &&
            !hasStartedInitialPhotoSearch &&
            uiState.recordId != null &&
            uiState.selectedLocation != null
        ) {
            hasStartedInitialPhotoSearch = true
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
        if (startWithPhotoSearch) {
            val retainedLocalPhotoIds = uiState.selectedPhotos
                .asSequence()
                .filter { photo -> photo.isUploaded }
                .map { photo -> photo.localPhotoId ?: photo.id }
                .filter(recommendationPagingState.selectedIds::contains)
                .toSet()
            uiState.selectedPhotos
                .filterNot { photo -> photo.isUploaded && (photo.localPhotoId ?: photo.id) in retainedLocalPhotoIds }
                .forEach { photo -> onPhotoRemoved(photo.id) }
            onPhotosAdded(selectedPhotos.filterNot { photo -> photo.id in retainedLocalPhotoIds })
        } else {
            replaceEditorPhotos(selectedPhotos)
        }
        photoMessage = null
        pendingSave = true
    }

    TripRecordBackground(
        modifier = modifier,
        backgroundColor = TripRecordPalette.current.pageBackground,
    ) {
        MapmoryPhotoExpansion(
            previewPhotoId?.let { id -> recommendationPagingState.photos.firstOrNull { it.id == id } },
        ) { previewPhoto ->
            if (previewPhoto != null) {
                PhotoPreviewViewer(
                    photo = previewPhoto,
                    fullResolutionBytes = fullResolutionPreviewBytes,
                    selected = previewPhoto.id in recommendationPagingState.selectedIds,
                    onToggle = {
                        val next = recommendationPagingState.toggleSelection(previewPhoto.id)
                        if (next == recommendationPagingState && previewPhoto.id !in next.selectedIds) {
                            previewPhotoId = null
                            showPhotoLimitMessage()
                        }
                        recommendationPagingState = next
                    },
                    onDismiss = { previewPhotoId = null },
                )
            } else {
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
                                locationSearchQuery = it.take(MaxPlaceQueryLength)
                                onPlaceSearchQueryChanged()
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
                                onPlaceSearchQueryChanged()
                                pendingLocation = location
                                locationSearchQuery = location.flowDisplayName(locations)
                                detailsErrorMessage = null
                            },
                            onPlaceCandidateSelected = { candidate ->
                                if (!uiState.isSelectingPlace) {
                                    logFieldInteraction("location")
                                    keyboard?.hide()
                                    onPlaceSearchQueryChanged()
                                    locationSearchQuery = candidate.name
                                    placeSelectionScope.launch {
                                        onPlaceCandidateSelected(candidate)?.let { location ->
                                            analytics.logEvent(
                                                MapmoryAnalyticsEvent.RECORD_LOCATION_SELECTED,
                                                mapOf(
                                                    "source" to "place_search",
                                                    "location_type" to location.type.name.lowercase(),
                                                ),
                                            )
                                            pendingLocation = location
                                        }
                                    }
                                }
                            },
                            onSelectedPlaceCleared = {
                                onSelectedPlaceCleared()
                                locationSearchQuery = uiState.selectedLocation
                                    ?.flowDisplayName(locations)
                                    .orEmpty()
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
                            isSelectingAll = isSelectingAllPhotos,
                            isRefreshingFilter = isRefreshingRecordedPhotoFilter,
                            actionLabel = saveActionLabel,
                            recordedPhotoIds = recordedPhotoIds,
                            excludeRecordedPhotos = excludeRecordedPhotos,
                            showRecordedFilter = !startWithPhotoSearch,
                            onRecordedFilterChanged = {
                                excludeRecordedPhotos = it
                                beginPhotoSearch(
                                    keepPhotoPickerVisible = true,
                                    shouldExcludeRecordedPhotos = it,
                                )
                            },
                            onBackClick = ::returnToPreviousStep,
                            onCompleteClick = ::completePhotoSelection,
                            onPickFromGallery = {
                                logFieldInteraction("photos")
                                analytics.logEvent(MapmoryAnalyticsEvent.PHOTO_PICKER_OPENED)
                                photoLibrary.pickFromGallery()
                            },
                            onPhotoPreview = { previewPhotoId = it.id },
                            onPhotoToggle = { photo ->
                                if (isRefreshingRecordedPhotoFilter) return@PhotoPickerStep
                                cancelSelectAllContinuation()
                                val next = recommendationPagingState.toggleSelection(photo.id)
                                if (next == recommendationPagingState && photo.id !in next.selectedIds) {
                                    previewPhotoId = null
                                    showPhotoLimitMessage()
                                }
                                recommendationPagingState = next
                            },
                            onPhotoSelectionChange = { photoId, selected ->
                                if (isRefreshingRecordedPhotoFilter) return@PhotoPickerStep
                                cancelSelectAllContinuation()
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
                                if (isRefreshingRecordedPhotoFilter) return@PhotoPickerStep
                                cancelSelectAllContinuation()
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
                                if (isRefreshingRecordedPhotoFilter || isSelectingAllPhotos) {
                                    return@PhotoPickerStep
                                }
                                if (recommendationPagingState.isAllSelectionActive()) {
                                    cancelSelectAllContinuation()
                                    recommendationPagingState = recommendationPagingState.copy(selectedIds = emptySet())
                                } else {
                                    isSelectingAllPhotos = true
                                    recommendationPagingState = recommendationPagingState.selectAllLoadedPhotos()
                                    photoLibrary.loadRecommendationPagesForSelection(
                                        recommendationPagingState.maxSelectionCount,
                                    )
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


    if (showPhotoLoadingBackConfirmation) {
        AlertDialog(
            onDismissRequest = { showPhotoLoadingBackConfirmation = false },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = true,
            ),
            shape = RoundedCornerShape(20.dp),
            containerColor = TripRecordPalette.current.surface,
            titleContentColor = TripRecordPalette.current.text,
            textContentColor = TripRecordPalette.current.bodyText,
            title = { Text("사진을 불러오는 중이에요") },
            text = {
                Text("불러오는 중에 뒤로 가면 사진을 다시 찾아야 합니다. 이전 단계로 가시겠습니까?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPhotoLoadingBackConfirmation = false
                        photoLibrary.cancelRecommendation()
                        isPreparingPhotoPreviews = false
                        isRefreshingRecordedPhotoFilter = false
                        onPhotoLoadingChanged(false)
                        if (startWithPhotoSearch) {
                            onConfirmedPhotoLoadingBackClick()
                        } else {
                            stepName = NewRecordFlowStep.LOCATION.name
                        }
                    },
                ) {
                    Text("이전 단계로", color = TripRecordPalette.current.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoLoadingBackConfirmation = false }) {
                    Text("계속 불러오기", color = TripRecordPalette.current.accent)
                }
            },
        )
    }

    if (showPhotoPickerBackConfirmation) {
        AlertDialog(
            onDismissRequest = { showPhotoPickerBackConfirmation = false },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = true,
            ),
            shape = RoundedCornerShape(20.dp),
            containerColor = TripRecordPalette.current.surface,
            titleContentColor = TripRecordPalette.current.text,
            textContentColor = TripRecordPalette.current.bodyText,
            title = { Text("사진 선택을 그만둘까요?") },
            text = {
                Text("이전 단계로 가면 선택한 사진과 검색 결과가 사라집니다. 계속할까요?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPhotoPickerBackConfirmation = false
                        photoLibrary.cancelRecommendation()
                        cancelSelectAllContinuation()
                        isPreparingPhotoPreviews = false
                        isRefreshingRecordedPhotoFilter = false
                        onPhotoLoadingChanged(false)
                        if (startWithPhotoSearch) {
                            onConfirmedPhotoLoadingBackClick()
                        } else {
                            stepName = NewRecordFlowStep.LOCATION.name
                        }
                    },
                ) {
                    Text("이전 단계로", color = TripRecordPalette.current.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoPickerBackConfirmation = false }) {
                    Text("계속 선택", color = TripRecordPalette.current.accent)
                }
            },
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
    onPlaceCandidateSelected: (PlaceCandidate) -> Unit,
    onSelectedPlaceCleared: () -> Unit,
    onBackClick: () -> Unit,
    onCompleteClick: () -> Unit,
) {
    val locationScrollState = rememberScrollState()
    val queryMatchesSelectedRegion = searchQuery.trim().equals(
        uiState.selectedLocation?.flowDisplayName(locations),
        ignoreCase = true,
    )
    LaunchedEffect(
        searchQuery,
        uiState.isSearchingPlaces,
        uiState.hasSearchedPlaces,
        uiState.placeSearchErrorMessage,
        uiState.placeSearchResults.size,
    ) {
        if (
            uiState.isPlaceSearchAvailable &&
            uiState.hasSearchedPlaces &&
            !uiState.isSearchingPlaces &&
            searchQuery.trim().length >= MinPlaceQueryLength &&
            !uiState.manualRegionRequired &&
            !queryMatchesSelectedRegion &&
            !searchQuery.trim().equals(uiState.selectedPlace?.name, ignoreCase = true)
        ) {
            withFrameNanos { }
            locationScrollState.animateScrollTo(locationScrollState.maxValue)
        }
    }
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
                .verticalScroll(locationScrollState)
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
            FlowSectionTitle(
                title = "장소",
                badge = "필수",
                helper = "지역은 한 글자부터, 장소는 두 글자부터 검색해요.",
                modifier = Modifier.padding(top = 24.dp),
            )
            LocationSearchField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.padding(top = 12.dp),
            )
            uiState.selectedPlace?.let { place ->
                SelectedPlaceCard(
                    place = place,
                    regionName = uiState.selectedLocation?.flowDisplayName(locations),
                    manualRegionRequired = uiState.manualRegionRequired,
                    onClear = onSelectedPlaceCleared,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (searchQuery.isNotBlank()) {
                if (
                    uiState.isPlaceSearchAvailable &&
                    searchQuery.trim().length >= MinPlaceQueryLength &&
                    !uiState.manualRegionRequired &&
                    !queryMatchesSelectedRegion &&
                    !searchQuery.trim().equals(uiState.selectedPlace?.name, ignoreCase = true)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "장소 검색 결과",
                            color = TripRecordPalette.current.headingText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        PlaceSearchResults(
                            results = uiState.placeSearchResults,
                            isLoading = uiState.isSearchingPlaces,
                            isSelecting = uiState.isSelectingPlace,
                            hasSearched = uiState.hasSearchedPlaces,
                            errorMessage = uiState.placeSearchErrorMessage,
                            onPlaceSelected = onPlaceCandidateSelected,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        uiState.placeSearchResults.firstOrNull()?.let { candidate ->
                            PlaceAttributionLinks(
                                attribution = candidate.attribution,
                                attributionUrl = candidate.attributionUrl,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                if (uiState.manualRegionRequired) {
                    Text(
                        text = "장소의 행정구역을 찾지 못했어요. 사진을 찾을 지역을 직접 선택해 주세요.",
                        color = TripRecordPalette.current.secondaryText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (searchResults.isNotEmpty()) {
                    Text(
                        text = "행정구역 검색 결과",
                        color = TripRecordPalette.current.headingText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    LocationSearchResults(
                        results = searchResults,
                        locations = locations,
                        selectedLocationId = uiState.selectedLocation?.id,
                        onLocationSelected = onLocationSelected,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            uiState.placeSelectionErrorMessage?.let { message ->
                Text(
                    text = message,
                    color = TripRecordPalette.current.danger,
                    fontSize = 12.sp,
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
private fun SelectedPlaceCard(
    place: PlaceReference,
    regionName: String?,
    manualRegionRequired: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TripRecordPalette.current.surfaceElevated)
            .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "선택한 장소",
                    color = TripRecordPalette.current.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = place.name,
                    color = TripRecordPalette.current.headingText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = onClear) {
                Text("해제", color = TripRecordPalette.current.accent)
            }
        }
        place.address?.let { address ->
            Text(
                text = address,
                color = TripRecordPalette.current.secondaryText,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = when {
                manualRegionRequired -> "사진을 찾을 행정구역을 아래에서 직접 선택해 주세요."
                regionName != null -> "사진 검색 지역: $regionName"
                else -> "장소의 행정구역을 확인하고 있어요."
            },
            color = TripRecordPalette.current.bodyText,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        PlaceAttributionLinks(
            attribution = place.attribution,
            attributionUrl = place.attributionUrl,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PlaceSearchResults(
    results: List<PlaceCandidate>,
    isLoading: Boolean,
    isSelecting: Boolean,
    hasSearched: Boolean,
    errorMessage: String?,
    onPlaceSelected: (PlaceCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading || isSelecting -> Row(
            modifier = modifier.padding(horizontal = 4.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = TripRecordPalette.current.accent,
                strokeWidth = 2.dp,
            )
            Text(
                text = if (isSelecting) "장소 정보를 확인하고 있어요." else "장소를 찾고 있어요.",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        errorMessage != null -> Text(
            text = errorMessage,
            color = TripRecordPalette.current.danger,
            fontSize = 13.sp,
            modifier = modifier.padding(horizontal = 4.dp, vertical = 14.dp),
        )
        results.isEmpty() && hasSearched -> Text(
            text = "장소 검색 결과가 없습니다.",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 13.sp,
            modifier = modifier.padding(horizontal = 4.dp, vertical = 14.dp),
        )
        results.isNotEmpty() -> LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 64.dp * 3)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(18.dp)),
        ) {
            items(results, key = PlaceCandidate::placeId) { candidate ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !isSelecting,
                            role = Role.Button,
                            onClick = { onPlaceSelected(candidate) },
                        )
                        .background(TripRecordPalette.current.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = candidate.name,
                        color = TripRecordPalette.current.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    candidate.address?.let { address ->
                        Text(
                            text = address,
                            color = TripRecordPalette.current.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceAttributionLinks(
    attribution: String?,
    attributionUrl: String?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = attribution ?: "© OpenStreetMap contributors",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (attributionUrl.isNullOrBlank()) Modifier else Modifier.clickable(
                        onClick = { runCatching { uriHandler.openUri(attributionUrl) } },
                    ),
                ),
        )
        Text(
            text = "Powered by Geoapify",
            color = TripRecordPalette.current.accent,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.clickable(
                onClick = { runCatching { uriHandler.openUri(GeoapifyUrl) } },
            ),
        )
    }
}

private const val GeoapifyUrl = "https://www.geoapify.com/"

@Composable
private fun FlowTopBar(
    title: String,
    onBackClick: () -> Unit,
    actionLabel: String? = null,
    actionStatusLabel: String? = null,
    reserveActionStatusSlot: Boolean = false,
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (actionEnabled || actionStatusLabel != null) {
                                    TripRecordPalette.current.accent
                                } else {
                                    TripRecordPalette.current.muted
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (reserveActionStatusSlot || actionStatusLabel != null) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .then(
                                            if (actionStatusLabel != null) {
                                                Modifier.semantics {
                                                    contentDescription = actionStatusLabel
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (actionStatusLabel != null) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.fillMaxSize(),
                                            color = TripRecordPalette.current.accent,
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            }
                        }
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
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
        }
        Text(
            text = helper,
            color = TripRecordPalette.current.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
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
                            text = "장소 또는 행정구역 검색",
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
private fun FlowSmallButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TripRecordPalette.current.primarySoft)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = TripRecordPalette.current.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
    isSelectingAll: Boolean,
    isRefreshingFilter: Boolean,
    actionLabel: String,
    recordedPhotoIds: Set<String>,
    excludeRecordedPhotos: Boolean,
    showRecordedFilter: Boolean,
    onRecordedFilterChanged: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPhotoPreview: (SelectedPhoto) -> Unit,
    onPhotoToggle: (SelectedPhoto) -> Unit,
    onPhotoSelectionChange: (photoId: String, selected: Boolean) -> Unit,
    onGroupToggle: (List<String>) -> Unit,
    onAllToggle: () -> Unit,
) {
    var photoGridColumns by rememberSaveable { mutableStateOf(DefaultPhotoGridColumns) }
    var accumulatedGridZoom by remember { mutableStateOf(1f) }
    val previewButtonSize by animateDpAsState(
        targetValue = when (photoGridColumns) {
            1 -> 48.dp
            2 -> 38.dp
            3 -> 32.dp
            else -> 27.dp
        },
        label = "photoPreviewButtonSize",
    )
    val previewIconSize by animateDpAsState(
        targetValue = when (photoGridColumns) {
            1 -> 24.dp
            2 -> 19.dp
            3 -> 16.dp
            else -> 14.dp
        },
        label = "photoPreviewIconSize",
    )
    val gridTransformableState = rememberTransformableState { _, zoomChange, _, _ ->
        accumulatedGridZoom *= zoomChange
        photoGridPinchResultAfterZoom(photoGridColumns, accumulatedGridZoom)?.let { result ->
            photoGridColumns = result.columnCount
            accumulatedGridZoom = result.accumulatedZoom
        }
    }
    LaunchedEffect(gridTransformableState.isTransformInProgress) {
        if (!gridTransformableState.isTransformInProgress) accumulatedGridZoom = 1f
    }
    val photoPickerItems = remember(pagingState.photos, photoGridColumns) {
        pagingState.photos.toPhotoPickerListItems(photoGridColumns)
    }
    val density = LocalDensity.current
    val autoScrollEdgeSize = with(density) { 104.dp.toPx() }
    val maximumAutoScrollStep = with(density) { 30.dp.toPx() }
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    var dragPointerPosition by remember { mutableStateOf<Offset?>(null) }
    val latestSelectedIds by rememberUpdatedState(pagingState.selectedIds)
    val orderedPhotoIds = remember(photoPickerItems) {
        photoPickerItems
            .filterIsInstance<PhotoPickerListItem.PhotoRow>()
            .flatMap { row -> row.photos }
            .map(SelectedPhoto::id)
    }
    val latestOrderedIds by rememberUpdatedState(orderedPhotoIds)
    val latestSelectionChange by rememberUpdatedState(onPhotoSelectionChange)
    val dragSelectionController = remember {
        PhotoDragSelectionController(
            selectedIds = { latestSelectedIds },
            orderedIds = { latestOrderedIds },
            maxSelectionCount = pagingState.maxSelectionCount,
            onSelectionChanged = { photoId, selected ->
                latestSelectionChange(photoId, selected)
            },
        )
    }
    LaunchedEffect(listState, dragPointerPosition, listBounds) {
        val pointer = dragPointerPosition ?: return@LaunchedEffect
        val bounds = listBounds ?: return@LaunchedEffect
        while (isActive) {
            val velocity = photoDragAutoScrollVelocity(
                pointerY = pointer.y,
                viewportTop = bounds.top,
                viewportBottom = bounds.bottom,
                edgeSize = autoScrollEdgeSize,
                maximumStep = maximumAutoScrollStep,
            )
            if (velocity != 0f) listState.scrollBy(velocity)
            delay(DragAutoScrollFrameMillis)
            dragSelectionController.moveTo(
                Offset(pointer.x, pointer.y.coerceIn(bounds.top + 1f, bounds.bottom - 1f)),
            )
        }
    }
    Column(Modifier.fillMaxSize()) {
        FlowTopBar(
            title = "사진 고르기",
            onBackClick = onBackClick,
            actionLabel = actionLabel,
            actionStatusLabel = when {
                isPreparing -> "저장 요청 중"
                isSelectingAll -> "사진 불러오는 중"
                isRefreshingFilter -> "필터 적용 중"
                else -> null
            },
            reserveActionStatusSlot = true,
            actionEnabled = pagingState.selectedIds.isNotEmpty() &&
                !isPreparing &&
                !isSelectingAll &&
                !isRefreshingFilter,
            onActionClick = onCompleteClick,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .transformable(
                        state = gridTransformableState,
                        canPan = { false },
                        lockRotationOnZoomPan = true,
                        enabled = pagingState.photos.isNotEmpty(),
                    )
                    .clipToBounds()
                    .onGloballyPositioned { coordinates -> listBounds = coordinates.boundsInRoot() }
                    .pointerInput(dragSelectionController) {
                        // The list owns the gesture even when its starting card leaves composition.
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val bounds = listBounds
                                if (bounds != null) {
                                    val position = bounds.topLeft + offset
                                    dragSelectionController.photoAt(position)?.let { id ->
                                        dragSelectionController.start(id, position)
                                        dragPointerPosition = position
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                if (dragPointerPosition != null) {
                                    change.consume()
                                    listBounds?.let { bounds ->
                                        val position = bounds.topLeft + change.position
                                        dragPointerPosition = position
                                        dragSelectionController.moveTo(position)
                                    }
                                }
                            },
                            onDragEnd = {
                                dragSelectionController.finish()
                                dragPointerPosition = null
                            },
                            onDragCancel = {
                                dragSelectionController.finish()
                                dragPointerPosition = null
                            },
                        )
                    },
                contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 24.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    PhotoPickerHeader(
                        locationName = locationName,
                        selectedCount = pagingState.selectedIds.size,
                        hasPhotos = pagingState.photos.isNotEmpty(),
                        allSelected = pagingState.isAllSelectionActive() && !isSelectingAll,
                        onAllToggle = onAllToggle,
                        onPickFromGallery = onPickFromGallery,
                    )
                    if (showRecordedFilter) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = excludeRecordedPhotos,
                                enabled = !isRefreshingFilter,
                                onCheckedChange = onRecordedFilterChanged,
                            )
                            Text(
                                text = "이미 기록에 포함된 사진 숨기기",
                                color = TripRecordPalette.current.bodyText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
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
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                if (pagingState.photos.isEmpty()) {
                    item {
                        EmptyPhotoPicker(
                            onPickFromGallery = onPickFromGallery,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                } else {
                    items(photoPickerItems, key = PhotoPickerListItem::key) { item ->
                        when (item) {
                            is PhotoPickerListItem.DateHeader -> PhotoDateHeader(
                                date = item.date,
                                photos = item.photos,
                                selectedIds = pagingState.selectedIds,
                                onGroupToggle = {
                                    onGroupToggle(item.photos.map(SelectedPhoto::id))
                                },
                                modifier = Modifier.animateItem().padding(top = 24.dp),
                            )

                            is PhotoPickerListItem.PhotoRow -> PhotoSelectionRow(
                                photos = item.photos,
                                columnCount = photoGridColumns,
                                previewButtonSize = previewButtonSize,
                                previewIconSize = previewIconSize,
                                selectedIds = pagingState.selectedIds,
                                onPhotoPreview = onPhotoPreview,
                                onPhotoToggle = onPhotoToggle,
                                dragSelectionController = dragSelectionController,
                                recordedPhotoIds = recordedPhotoIds,
                                modifier = Modifier.animateItem().padding(
                                    top = if (item.isFirstRowInDate) 12.dp else 10.dp,
                                ),
                            )
                        }
                    }
                }
                if (isLoadingMore) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
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
            PhotoPickerScrollBar(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 5.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun PhotoPickerScrollBar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = listState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.totalItemsCount <= 0) return
    val density = LocalDensity.current
    val itemSpacing = with(density) { 0.dp.roundToPx() }
    val contentPaddingBefore = with(density) { 24.dp.roundToPx() }
    val contentPaddingAfter = with(density) { 36.dp.roundToPx() }
    val knownItemSizes = remember(listState) { mutableMapOf<Int, Int>() }
    layoutInfo.visibleItemsInfo.forEach { item -> knownItemSizes[item.index] = item.size }
    val estimatedItemSize = knownItemSizes.values.average().roundToInt().coerceAtLeast(1)
    val estimatedItemSizes = (0 until layoutInfo.totalItemsCount).map { index ->
        knownItemSizes[index] ?: estimatedItemSize
    }
    val estimatedContentSize = contentPaddingBefore + contentPaddingAfter +
        estimatedItemSizes.sum() +
        itemSpacing * (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
    val estimatedScrollOffset = (0 until listState.firstVisibleItemIndex).sumOf { index ->
        (knownItemSizes[index] ?: estimatedItemSize) + itemSpacing
    } + listState.firstVisibleItemScrollOffset
    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val scrollOffset = when {
        !listState.canScrollBackward -> 0
        !listState.canScrollForward -> estimatedContentSize - viewportSize
        else -> estimatedScrollOffset
    }
    val metrics = photoPickerScrollBarMetrics(
        scrollOffset = scrollOffset,
        viewportSize = viewportSize,
        contentSize = estimatedContentSize,
    ) ?: return
    val trackColor = TripRecordPalette.current.line.copy(alpha = 0.65f)
    val thumbColor = TripRecordPalette.current.accent.copy(alpha = 0.9f)
    var scrollRequestVersion by remember { mutableStateOf(0) }
    var requestedScroll by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    LaunchedEffect(requestedScroll) {
        val fraction = requestedScroll?.second ?: return@LaunchedEffect
        val target = photoPickerScrollTarget(
            scrollFraction = fraction,
            viewportSize = viewportSize,
            itemSizes = estimatedItemSizes,
            itemSpacing = itemSpacing,
            contentPaddingBefore = contentPaddingBefore,
            contentPaddingAfter = contentPaddingAfter,
        ) ?: return@LaunchedEffect
        listState.scrollToItem(target.itemIndex, target.itemScrollOffset)
    }
    Canvas(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .testTag("photo-picker-scrollbar")
            .semantics { contentDescription = "사진 목록 스크롤바" }
            .pointerInput(estimatedContentSize, viewportSize) {
                fun updateScrollPosition(pointerY: Float) {
                    scrollRequestVersion += 1
                    requestedScroll = scrollRequestVersion to
                        (pointerY / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
                detectDragGestures(
                    onDragStart = { offset -> updateScrollPosition(offset.y) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateScrollPosition(change.position.y)
                    },
                )
            },
    ) {
        val thumbHeight = (size.height * metrics.thumbFraction)
            .coerceAtLeast(44.dp.toPx())
            .coerceAtMost(size.height)
        val thumbOffset = (size.height - thumbHeight) * metrics.scrollFraction
        val barWidth = 4.dp.toPx()
        val barOffsetX = (size.width - barWidth) / 2f
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(barOffsetX, 0f),
            size = androidx.compose.ui.geometry.Size(barWidth, size.height),
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(barOffsetX, thumbOffset),
            size = androidx.compose.ui.geometry.Size(barWidth, thumbHeight),
            cornerRadius = cornerRadius,
        )
    }
}

@Composable
private fun PhotoPickerHeader(
    locationName: String,
    selectedCount: Int,
    hasPhotos: Boolean,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasPhotos) {
                FlowSmallButton(
                    label = if (allSelected) "추천 사진 선택 해제" else "추천 사진 모두 선택",
                    onClick = onAllToggle,
                    modifier = Modifier.weight(1f),
                )
            }
            FlowSmallButton(
                label = "사진첩에서 선택",
                onClick = onPickFromGallery,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "날짜별 선택은 현재 불러온 사진에 적용돼요.\n추천 사진은 최대 ${TripRecordPhotoRules.MaxPhotosPerRecord}장까지 선택할 수 있어요.",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EmptyPhotoPicker(
    onPickFromGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
private fun PhotoDateHeader(
    date: String,
    photos: List<SelectedPhoto>,
    selectedIds: Set<String>,
    onGroupToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allSelected = photos.all { it.id in selectedIds }
    Row(
        modifier = modifier.fillMaxWidth(),
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
        FlowSmallButton(
            label = if (allSelected) "이 날짜 선택 해제" else "이 날짜 사진 선택",
            onClick = onGroupToggle,
        )
    }
}

@Composable
private fun PhotoSelectionRow(
    photos: List<SelectedPhoto>,
    columnCount: Int,
    previewButtonSize: Dp,
    previewIconSize: Dp,
    selectedIds: Set<String>,
    onPhotoPreview: (SelectedPhoto) -> Unit,
    onPhotoToggle: (SelectedPhoto) -> Unit,
    dragSelectionController: PhotoDragSelectionController,
    recordedPhotoIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        photos.forEach { photo ->
            PhotoSelectionCard(
                photo = photo,
                selected = photo.id in selectedIds,
                onPreview = { onPhotoPreview(photo) },
                onToggle = { onPhotoToggle(photo) },
                dragSelectionController = dragSelectionController,
                recorded = photo.id in recordedPhotoIds,
                previewButtonSize = previewButtonSize,
                previewIconSize = previewIconSize,
                modifier = Modifier.weight(1f),
            )
        }
        repeat((columnCount - photos.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotoSelectionCard(
    photo: SelectedPhoto,
    previewButtonSize: Dp,
    previewIconSize: Dp,
    selected: Boolean,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
    dragSelectionController: PhotoDragSelectionController,
    recorded: Boolean,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(photo.id, dragSelectionController) {
        onDispose { dragSelectionController.remove(photo.id) }
    }
    val cardShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(cardShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) TripRecordPalette.current.accent else TripRecordPalette.current.photoGalleryBorder,
                shape = cardShape,
            )
            .onGloballyPositioned { layoutCoordinates ->
                dragSelectionController.updateBounds(
                    photoId = photo.id,
                    bounds = Rect(
                        layoutCoordinates.positionInRoot(),
                        androidx.compose.ui.geometry.Size(
                            layoutCoordinates.size.width.toFloat(),
                            layoutCoordinates.size.height.toFloat(),
                        ),
                    ),
                )
            }
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = if (selected) {
                    "${photo.displayName}, 선택됨. 누르면 선택 해제, 길게 누르고 드래그해 연속 선택 해제"
                } else {
                    "${photo.displayName}, 선택 안 됨. 누르면 선택, 길게 누르고 드래그해 연속 선택"
                }
            }
            .testTag("new-record-photo-${photo.id}"),
    ) {
        MapmoryAsyncImage(
            imageBytes = photo.previewBytes,
            imageUri = photo.previewUri ?: photo.fullResolutionUri.takeIf { photo.previewBytes == null },
            sharedImageKey = "picker:${photo.id}",
            cacheKey = "picker-preview:${photo.id}",
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
        )
        if (recorded) {
            Text(
                "기록됨 · 저장 중 포함",
                color = TripRecordPalette.current.contentOnMedia,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(TripRecordPalette.current.mediaScrim, RoundedCornerShape(6.dp))
                    .padding(6.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(previewButtonSize)
                .background(
                    if (selected) {
                        TripRecordPalette.current.accent
                    } else {
                        TripRecordPalette.current.mediaScrim
                    },
                    CircleShape,
                )
                .clickable(role = Role.Button, onClick = onPreview)
                .semantics { contentDescription = "${photo.displayName} 크게 보기" },
            contentAlignment = Alignment.Center,
        ) {
            SearchIcon(
                color = TripRecordPalette.current.contentOnMedia,
                modifier = Modifier.size(previewIconSize),
            )
        }
    }
}

@Composable
private fun PhotoPreviewViewer(
    photo: SelectedPhoto,
    fullResolutionBytes: ByteArray?,
    selected: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val usePreview = LocalMapmoryImageTransitionScope.current?.isTransitionActive == true
    MapmoryPhotoViewer(
        title = photo.displayName,
        onClose = onDismiss,
        closeContentDescription = "사진 고르기로 돌아가기",
        bottomContent = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    photo.capturedAt?.toFlowDateDisplay() ?: "촬영일 미상",
                    color = Color.White, modifier = Modifier.weight(1f),
                )
                Text(
                    if (selected) "선택 해제" else "앨범에 추가",
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
    ) {
        MapmoryAsyncImage(
            imageBytes = if (usePreview) photo.previewBytes else fullResolutionBytes ?: photo.previewBytes,
            imageUri = if (usePreview) {
                photo.previewUri ?: photo.fullResolutionUri.takeIf { photo.previewBytes == null }
            } else photo.fullResolutionUri.takeIf { fullResolutionBytes == null },
            fallbackUri = photo.previewUri,
            fallbackBytes = photo.previewBytes,
            cacheKey = if (usePreview) "picker-preview:${photo.id}" else "picker-full:${photo.id}",
            sharedImageKey = "picker:${photo.id}",
            contentDescription = "${photo.displayName} 확대 사진",
            blackLoadingBackground = true,
            modifier = Modifier.fillMaxSize().padding(vertical = 104.dp),
            shape = RectangleShape,
            contentScale = ContentScale.Fit,
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

internal sealed interface PhotoPickerListItem {
    val key: String

    data class DateHeader(
        val date: String,
        val photos: List<SelectedPhoto>,
    ) : PhotoPickerListItem {
        override val key: String = "date:$date"
    }

    data class PhotoRow(
        val photos: List<SelectedPhoto>,
        val isFirstRowInDate: Boolean,
    ) : PhotoPickerListItem {
        override val key: String = "row:${photos.first().id}"
    }
}

internal fun List<SelectedPhoto>.toPhotoPickerListItems(
    columnCount: Int = DefaultPhotoGridColumns,
): List<PhotoPickerListItem> =
    groupBy { photo -> photo.capturedAt ?: "촬영일 미상" }
        .entries
        .sortedByDescending { entry ->
            entry.key.takeUnless { it == "촬영일 미상" }.orEmpty()
        }
        .flatMap { entry ->
            buildList {
                add(PhotoPickerListItem.DateHeader(entry.key, entry.value))
                entry.value.chunked(columnCount.coerceAtLeast(1)).forEachIndexed { index, photos ->
                    add(PhotoPickerListItem.PhotoRow(photos, isFirstRowInDate = index == 0))
                }
            }
        }

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

internal fun PhotoRecommendationPagingState.isAllSelectionActive(): Boolean =
    photos.isNotEmpty() && selectedIds.isNotEmpty() &&
        (selectedIds.size >= maxSelectionCount || photos.all { photo -> photo.id in selectedIds })

internal fun PhotoRecommendationPagingState.toggleAllPhotos(): PhotoRecommendationPagingState {
    if (isAllSelectionActive()) return copy(selectedIds = emptySet())
    val selected = photos.asSequence()
        .map(SelectedPhoto::id)
        .distinct()
        .take(maxSelectionCount)
        .toSet()
    return copy(selectedIds = selected)
}
