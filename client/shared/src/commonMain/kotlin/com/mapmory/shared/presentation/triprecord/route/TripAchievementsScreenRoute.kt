package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.mapmory.shared.presentation.triprecord.screen.TripAchievementScreen
import com.mapmory.shared.presentation.triprecord.viewmodel.TripStatisticsViewModel
import kotlinx.coroutines.launch

@Composable
internal fun TripAchievementsScreenRoute(
    viewModel: TripStatisticsViewModel,
    tripRecordRevision: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel, tripRecordRevision) {
        viewModel.refreshIfNeeded(dataRevision = tripRecordRevision)
    }

    TripAchievementScreen(
        statisticsUiState = viewModel.uiState,
        onBack = onBack,
        onRetry = { scope.launch { viewModel.refresh(dataRevision = tripRecordRevision) } },
        modifier = modifier,
    )
}
