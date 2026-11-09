package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.mapmory.shared.analytics.emit
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.navigation.PlatformBackHandler
import com.mapmory.shared.presentation.triprecord.screen.TripRecordDetailScreen
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordDetailViewModel
import kotlinx.coroutines.launch

@Composable
internal fun TripRecordDetailRoute(
    recordId: Long,
    isNotificationEntry: Boolean = false,
    accountGeneration: Long = 0L,
    tripRecordRevision: Long,
    viewModel: TripRecordDetailViewModel,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    backHandlerOwnerId: String,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenProfile: () -> Unit,
    initialLocationName: String? = null,
    initialLatestDate: String? = null,
    initialPhotoCount: Int? = null,
    isTimelineLayout: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val analytics = LocalMapmoryAnalytics.current
    var photoViewerBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var photoViewerForwardHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    val latestForwardHandler by rememberUpdatedState { photoViewerForwardHandler?.invoke() == true }
    val latestBackHandler by rememberUpdatedState {
        photoViewerBackHandler?.invoke() == true
    }

    DisposableEffect(viewModel, backHandlerRegistry) {
        val registration = backHandlerRegistry.register(
            backHandlerOwnerId,
            gestureBack = { latestBackHandler() },
            gestureForward = { latestForwardHandler() },
        ) {
            latestBackHandler()
        }
        onDispose { backHandlerRegistry.unregister(registration) }
    }
    PlatformBackHandler(
        enabled = photoViewerBackHandler != null,
        onBack = { latestBackHandler() },
    )

    LaunchedEffect(recordId) {
        analytics.logEvent(
            MapmoryAnalyticsEvent.SCREEN_VIEW,
            mapOf("screen_name" to "record_detail", "record_id" to recordId.toString()),
        )
    }

    var loadedAccountGeneration by remember(viewModel, recordId) { mutableStateOf(accountGeneration) }
    LaunchedEffect(viewModel, recordId, tripRecordRevision, isNotificationEntry, accountGeneration) {
        if (isNotificationEntry || loadedAccountGeneration != accountGeneration) {
            viewModel.load(recordId)
        } else {
            viewModel.loadIfNeeded(recordId, tripRecordRevision)
        }
        loadedAccountGeneration = accountGeneration
    }

    var reportedView by rememberSaveable(recordId) { mutableStateOf(false) }
    val loadedRecord = (viewModel.uiState as? TripRecordDetailUiState.Success)?.record
    LaunchedEffect(recordId, loadedRecord?.id) {
        if (!reportedView && loadedRecord?.id == recordId) {
            analytics.emit(MapmoryAnalyticsEvent.RECORD_VIEWED, mapOf("record_id" to recordId.toString()))
            reportedView = true
        }
    }

    TripRecordDetailScreen(
        modifier = modifier,
        uiState = viewModel.uiState,
        notificationEntryError = if (isNotificationEntry) {
            "기록을 불러오지 못했어요. 로그인 계정과 연결 상태를 확인한 뒤 다시 시도해 주세요."
        } else null,
        onRetryClick = if (isNotificationEntry) ({ scope.launch { viewModel.load(recordId) } }) else null,
        initialLocationName = initialLocationName,
        initialLatestDate = initialLatestDate,
        initialPhotoCount = initialPhotoCount,
        isTimelineLayout = isTimelineLayout,
        onBackClick = onBack,
        onEditClick = {
            analytics.emit(MapmoryAnalyticsEvent.RECORD_EDIT_STARTED, mapOf("record_id" to recordId.toString()))
            onEdit(recordId)
        },
        onDeleteClick = {
            analytics.emit(MapmoryAnalyticsEvent.RECORD_DELETE_STARTED, mapOf("record_id" to recordId.toString()))
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
        onInternalBackHandlerChanged = { handler -> photoViewerBackHandler = handler },
        onForwardHandlerChanged = { handler -> photoViewerForwardHandler = handler },
    )
}
