package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.app.BackgroundTripRecordSave
import com.mapmory.shared.data.settings.OnboardingPreference
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.model.TripRecordSort
import com.mapmory.shared.domain.repository.TripStatisticsRepository
import com.mapmory.shared.domain.repository.ShareVideoRepository
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.presentation.triprecord.TripRecordListLayout
import com.mapmory.shared.presentation.triprecord.screen.TripRecordListScreen
import com.mapmory.shared.presentation.triprecord.state.TripRecordFilterUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordListViewModel
import kotlinx.coroutines.launch

@Composable
internal fun TripRecordListRoute(
    viewModel: TripRecordListViewModel,
    initialLocationId: Long?,
    tripRecordRevision: Long,
    pendingSaves: List<BackgroundTripRecordSave>,
    onRetryPendingSave: (Long) -> Unit,
    onDismissPendingSave: (Long) -> Unit,
    onOpenMap: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenDetail: (Long, String?, String?, Int?) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSettings: () -> Unit,
    shareVideoRepository: ShareVideoRepository?,
    shareVideoJobStore: ShareVideoJobStore,
    onboardingPreference: OnboardingPreference,
    tripStatisticsRepository: TripStatisticsRepository,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    backHandlerOwnerId: String,
    layout: TripRecordListLayout = TripRecordListLayout.PHOTO,
    showLayoutSwitcher: Boolean = false,
    onLayoutChange: (TripRecordListLayout) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val analytics = LocalMapmoryAnalytics.current

    LaunchedEffect(Unit) {
        analytics.logEvent(
            MapmoryAnalyticsEvent.SCREEN_VIEW,
            mapOf("screen_name" to "journal"),
        )
    }

    LaunchedEffect(viewModel, initialLocationId, tripRecordRevision, layout) {
        viewModel.refreshIfNeeded(
            initialLocationId,
            tripRecordRevision,
            sort = if (layout == TripRecordListLayout.TIMELINE) {
                TripRecordSort.TRAVEL_DATE_DESC
            } else {
                TripRecordSort.CREATED_AT_DESC
            },
        )
        if (layout == TripRecordListLayout.COLLECTION && viewModel.uiState is TripRecordListUiState.Success) {
            viewModel.loadPhotoCollectionIndex()
        }
    }

    // 기록이 바뀌어 다시 불러오는 동안에도 마지막 국가 수를 유지해 영상 카드 높이가 흔들리지 않게 한다.
    var countryCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var countryCountFailed by remember(tripStatisticsRepository, tripRecordRevision) { mutableStateOf(false) }
    var countryCountAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(shareVideoRepository, tripStatisticsRepository, tripRecordRevision, countryCountAttempt) {
        if (shareVideoRepository == null) return@LaunchedEffect
        countryCountFailed = false
        val result = tripStatisticsRepository.getStatistics()
        // 실패하면 마지막 값을 그대로 두고, 재시도 안내는 countryCountFailed로 보여 준다.
        result.getOrNull()?.let { countryCount = it.visitedCountryCodes.toSet().size }
        countryCountFailed = result.isFailure
    }

    var hasSeenVideoPreview by remember(onboardingPreference) {
        mutableStateOf(onboardingPreference.hasSeenVideoPreview())
    }

    TripRecordListScreen(
        modifier = modifier,
        shareVideoRepository = shareVideoRepository,
        shareVideoJobStore = shareVideoJobStore,
        videoCountryCount = countryCount,
        hasSeenVideoPreview = hasSeenVideoPreview,
        onVideoPreviewOpened = {
            onboardingPreference.markVideoPreviewSeen()
            hasSeenVideoPreview = true
        },
        retryVideoCountryCount = if (countryCountFailed) ({ countryCountAttempt++ }) else null,
        uiState = viewModel.uiState,
        selectedLocationName = viewModel.selectedLocationName,
        filter = TripRecordFilterUiState(
            locationId = viewModel.query.locationId,
            tags = viewModel.availableTags,
            selectedTagId = viewModel.query.tagId,
        ),
        pendingSaves = pendingSaves,
        layout = layout,
        showLayoutSwitcher = showLayoutSwitcher,
        onLayoutChange = onLayoutChange,
        backHandlerRegistry = backHandlerRegistry,
        backHandlerOwnerId = backHandlerOwnerId,
        photoCollectionLoadState = viewModel.photoCollectionLoadState,
        photoCollectionIndexState = viewModel.photoCollectionIndexState,
        onLoadPhotoCollection = { locationId, locationName ->
            scope.launch { viewModel.loadPhotoCollection(locationId, locationName) }
        },
        onLoadPhotoCollectionIndex = {
            scope.launch { viewModel.loadPhotoCollectionIndex() }
        },
        onRetryPendingSave = onRetryPendingSave,
        onDismissPendingSave = onDismissPendingSave,
        onLoadMore = {
            scope.launch { viewModel.loadNextPage() }
        },
        onCreateClick = onOpenEditor,
        onMapClick = onOpenMap,
        onRecordClick = { recordId ->
            val records = (viewModel.uiState as? com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState.Success)?.records
            val record = records?.firstOrNull { it.id == recordId }
            onOpenDetail(
                recordId,
                record?.locationName,
                record?.endDate?.takeIf { it.isNotBlank() } ?: record?.startDate,
                record?.photoCount,
            )
        },
        onRetryClick = {
            analytics.logEvent(MapmoryAnalyticsEvent.JOURNAL_RETRY_CLICKED)
            scope.launch { retryTripRecordList(viewModel, initialLocationId, layout) }
        },
        onProfileClick = onOpenProfile,
        onSettingsClick = onOpenSettings,
        onGroupClick = onOpenGroups,
    )
}

internal suspend fun retryTripRecordList(
    viewModel: TripRecordListViewModel,
    locationId: Long?,
    layout: TripRecordListLayout,
) {
    viewModel.refresh(locationId)
    val refreshedState = viewModel.uiState as? TripRecordListUiState.Success ?: return
    if (layout == TripRecordListLayout.COLLECTION && refreshedState.refreshError == null) {
        viewModel.loadPhotoCollectionIndex()
    }
}
