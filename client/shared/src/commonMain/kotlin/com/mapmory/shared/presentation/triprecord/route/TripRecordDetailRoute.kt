package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.mapmory.shared.presentation.triprecord.screen.TripRecordDetailScreen
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordDetailViewModel
import kotlinx.coroutines.launch

@Composable
internal fun TripRecordDetailRoute(
    recordId: Long,
    tripRecordRevision: Long,
    viewModel: TripRecordDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel, recordId, tripRecordRevision) {
        viewModel.load(recordId)
    }

    TripRecordDetailScreen(
        modifier = modifier,
        uiState = viewModel.uiState,
        onBackClick = onBack,
        onEditClick = { onEdit(recordId) },
        onDeleteClick = {
            scope.launch {
                if (viewModel.delete()) {
                    onDeleted()
                }
            }
        },
        onMapClick = onOpenMap,
        onRecordClick = onOpenRecords,
        onCreateClick = onOpenEditor,
        onProfileClick = onOpenProfile,
    )
}
