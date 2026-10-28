package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.app.BackgroundTripRecordSave
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.model.TripRecordSort
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
    onOpenSettings: () -> Unit,
    shareVideoRepository: ShareVideoRepository?,
    shareVideoJobStore: ShareVideoJobStore,
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

    TripRecordListScreen(
        modifier = modifier,
        shareVideoRepository = shareVideoRepository,
        shareVideoJobStore = shareVideoJobStore,
        uiState = viewModel.uiState,
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
        onLoadPhotoCollection = { locationName ->
            scope.launch { viewModel.loadPhotoCollection(locationName) }
        },
        onLoadPhotoCollectionIndex = {
            scope.launch { viewModel.loadPhotoCollectionIndex() }
        },
        onRetryPendingSave = onRetryPendingSave,
        onDismissPendingSave = onDismissPendingSave,
        onPreviousPageClick = {
            scope.launch { viewModel.previousPage() }
        },
        onNextPageClick = {
            scope.launch { viewModel.nextPage() }
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
            scope.launch { viewModel.refresh(initialLocationId) }
        },
        onProfileClick = onOpenProfile,
        onSettingsClick = onOpenSettings,
    )
}
