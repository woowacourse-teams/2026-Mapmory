package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mapmory.shared.presentation.triprecord.screen.TripProfileScreen

@Composable
internal fun TripProfileRoute(
    onOpenMap: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TripProfileScreen(
        modifier = modifier,
        onMapClick = onOpenMap,
        onRecordClick = onOpenRecords,
        onCreateClick = onOpenEditor,
        onProfileClick = onOpenProfile,
    )
}
