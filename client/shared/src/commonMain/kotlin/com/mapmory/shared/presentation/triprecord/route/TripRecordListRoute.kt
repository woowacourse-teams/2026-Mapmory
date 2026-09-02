package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.presentation.triprecord.screen.TripRecordListScreen
import com.mapmory.shared.presentation.triprecord.state.TripRecordFilterUiState
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordListViewModel
import kotlinx.coroutines.launch

@Composable
internal fun TripRecordListRoute(
    viewModel: TripRecordListViewModel,
    initialLocationId: Long?,
    tripRecordRevision: Long,
    onOpenMap: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenProfile: () -> Unit,
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

    LaunchedEffect(viewModel, initialLocationId, tripRecordRevision) {
        viewModel.refresh(initialLocationId)
    }

    TripRecordListScreen(
        modifier = modifier,
        uiState = viewModel.uiState,
        filter = TripRecordFilterUiState(
            locationId = viewModel.query.locationId,
            tags = viewModel.availableTags,
            selectedTagId = viewModel.query.tagId,
        ),
        onTagClick = { tagId ->
            scope.launch { viewModel.selectTag(tagId) }
        },
        onPreviousPageClick = {
            scope.launch { viewModel.previousPage() }
        },
        onNextPageClick = {
            scope.launch { viewModel.nextPage() }
        },
        onCreateClick = onOpenEditor,
        onMapClick = onOpenMap,
        onRecordClick = onOpenDetail,
        onRetryClick = {
            scope.launch { viewModel.refresh(initialLocationId) }
        },
        onProfileClick = onOpenProfile,
    )
}
