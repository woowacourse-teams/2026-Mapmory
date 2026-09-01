package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.presentation.triprecord.screen.TripRecordEditorScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordEditorViewModel
import kotlinx.coroutines.launch

@Composable
internal fun TripRecordEditorRoute(
    recordId: Long?,
    selectedLocationId: Long?,
    viewModel: TripRecordEditorViewModel,
    regionCatalog: RegionCatalog,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    onBack: () -> Unit,
    onSaved: (wasEditing: Boolean, recordId: Long) -> Unit,
    onOpenMap: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val analytics = LocalMapmoryAnalytics.current
    var pendingEditorExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPhotoLoadingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isPhotoLoadingSaveConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, recordId, selectedLocationId) {
        viewModel.initialize(
            recordId = recordId,
            selectedLocation = selectedLocationId?.let(regionCatalog::findById),
        )
    }

    LaunchedEffect(Unit) {
        analytics.logEvent(
            MapmoryAnalyticsEvent.SCREEN_VIEW,
            mapOf("screen_name" to "record_editor"),
        )
    }

    fun requestExit(exit: () -> Unit) {
        when {
            viewModel.uiState.isPhotoLoading -> {
                isPhotoLoadingSaveConfirmation = false
                pendingPhotoLoadingAction = exit
            }
            viewModel.uiState.isDirty -> pendingEditorExit = exit
            else -> exit()
        }
    }

    fun save() {
        scope.launch {
            val mode = if (recordId == null) "create" else "edit"
            analytics.logEvent(
                MapmoryAnalyticsEvent.RECORD_SAVE_STARTED,
                mapOf("mode" to mode),
            )
            if (viewModel.save()) {
                analytics.logEvent(
                    MapmoryAnalyticsEvent.RECORD_SAVE_COMPLETED,
                    mapOf("mode" to mode),
                )
                viewModel.savedRecordId?.let { savedId ->
                    onSaved(recordId != null, savedId)
                }
            } else {
                analytics.logEvent(
                    MapmoryAnalyticsEvent.RECORD_SAVE_FAILED,
                    mapOf("mode" to mode),
                )
            }
        }
    }

    val latestBackHandler = rememberUpdatedState {
        requestExit(onBack)
        true
    }
    DisposableEffect(viewModel, backHandlerRegistry) {
        val registration = backHandlerRegistry.register {
            latestBackHandler.value()
        }
        onDispose {
            backHandlerRegistry.unregister(registration)
        }
    }

    TripRecordEditorScreen(
        modifier = modifier,
        uiState = viewModel.uiState,
        locations = regionCatalog.locations,
        onLocationSelected = viewModel::selectLocation,
        onLocationTouched = viewModel::touchLocation,
        onTitleChanged = viewModel::updateTitle,
        onContentChanged = viewModel::updateContent,
        onStartDateChanged = viewModel::updateStartDate,
        onEndDateChanged = viewModel::updateEndDate,
        onPhotosAdded = viewModel::addPhotos,
        onPhotoRemoved = viewModel::removeMediaObjectKey,
        onPhotoLoadingChanged = viewModel::setPhotoLoading,
        onSaveClick = {
            if (viewModel.uiState.isPhotoLoading) {
                isPhotoLoadingSaveConfirmation = true
                pendingPhotoLoadingAction = {
                    viewModel.setPhotoLoading(false)
                    save()
                }
            } else {
                save()
            }
        },
        onBackClick = { requestExit(onBack) },
        onMapClick = { requestExit(onOpenMap) },
        onRecordClick = { requestExit(onOpenRecords) },
        onProfileClick = { requestExit(onOpenProfile) },
    )

    pendingEditorExit?.let { exit ->
        EditorConfirmationDialog(
            title = "작성 중인 기록이 있어요",
            message = "지금 나가면 작성한 내용이 사라집니다. 그래도 나갈까요?",
            confirmLabel = "나가기",
            confirmColor = TripRecordPalette.current.danger,
            onConfirm = {
                pendingEditorExit = null
                exit()
            },
            onDismiss = { pendingEditorExit = null },
        )
    }

    pendingPhotoLoadingAction?.let { action ->
        EditorConfirmationDialog(
            title = "사진을 불러오는 중이에요",
            message = if (isPhotoLoadingSaveConfirmation) {
                "지금 저장하면 불러오는 중인 사진은 기록에 포함되지 않아요. 현재 내용만 저장할까요?"
            } else {
                "지금 나가면 불러오는 사진과 작성 중인 내용은 저장되지 않아요. 그래도 나갈까요?"
            },
            confirmLabel = if (isPhotoLoadingSaveConfirmation) "현재 내용 저장" else "나가기",
            confirmColor = if (isPhotoLoadingSaveConfirmation) {
                TripRecordPalette.current.accent
            } else {
                TripRecordPalette.current.danger
            },
            onConfirm = {
                pendingPhotoLoadingAction = null
                action()
            },
            onDismiss = { pendingPhotoLoadingAction = null },
        )
    }
}

@Composable
private fun EditorConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        shape = RoundedCornerShape(20.dp),
        containerColor = TripRecordPalette.current.surface,
        titleContentColor = TripRecordPalette.current.text,
        textContentColor = TripRecordPalette.current.bodyText,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = confirmColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("계속 작성", color = TripRecordPalette.current.accent)
            }
        },
    )
}
