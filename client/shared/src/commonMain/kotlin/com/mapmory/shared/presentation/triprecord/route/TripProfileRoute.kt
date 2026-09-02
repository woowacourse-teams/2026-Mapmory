package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.mapmory.shared.presentation.triprecord.screen.TripProfileScreen
import com.mapmory.shared.presentation.triprecord.viewmodel.TripStatisticsViewModel
import kotlinx.coroutines.launch

@Composable
internal fun TripProfileRoute(
    viewModel: TripStatisticsViewModel,
    tripRecordRevision: Long,
    onOpenMap: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel, tripRecordRevision) {
        viewModel.refresh(dataRevision = tripRecordRevision)
    }

    TripProfileScreen(
        modifier = modifier,
        statisticsUiState = viewModel.uiState,
        onMapClick = onOpenMap,
        onRecordClick = onOpenRecords,
        onCreateClick = onOpenEditor,
        onProfileClick = onOpenProfile,
        onRetryClick = {
            scope.launch { viewModel.refresh(dataRevision = tripRecordRevision) }
        },
    )
}
