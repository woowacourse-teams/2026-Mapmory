package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.repository.ShareVideoRepository
import com.mapmory.shared.presentation.triprecord.screen.TripProfileScreen
import com.mapmory.shared.presentation.triprecord.viewmodel.TripStatisticsViewModel
import kotlinx.coroutines.launch

@Composable
internal fun TripProfileRoute(
    viewModel: TripStatisticsViewModel,
    tripRecordRevision: Long,
    shareVideoRepository: ShareVideoRepository?,
    shareVideoJobStore: ShareVideoJobStore,
    onOpenMap: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSettings: () -> Unit,
    showTravelAchievements: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val analytics = LocalMapmoryAnalytics.current

    LaunchedEffect(Unit) {
        analytics.logEvent(
            MapmoryAnalyticsEvent.SCREEN_VIEW,
            mapOf("screen_name" to "statistics"),
        )
    }

    LaunchedEffect(viewModel, tripRecordRevision) {
        viewModel.refreshIfNeeded(dataRevision = tripRecordRevision)
    }

    TripProfileScreen(
        modifier = modifier,
        statisticsUiState = viewModel.uiState,
        shareVideoRepository = shareVideoRepository,
        shareVideoJobStore = shareVideoJobStore,
        showTravelAchievements = showTravelAchievements,
        onMapClick = onOpenMap,
        onRecordClick = onOpenRecords,
        onCreateClick = onOpenEditor,
        onProfileClick = onOpenProfile,
        onSettingsClick = onOpenSettings,
        onGroupClick = onOpenGroups,
        onRetryClick = {
            analytics.logEvent(MapmoryAnalyticsEvent.STATISTICS_RETRY_CLICKED)
            scope.launch { viewModel.refresh(dataRevision = tripRecordRevision) }
        },
    )
}
