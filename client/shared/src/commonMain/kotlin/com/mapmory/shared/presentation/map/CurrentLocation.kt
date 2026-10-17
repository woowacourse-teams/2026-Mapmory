package com.mapmory.shared.presentation.map

import androidx.compose.runtime.Composable
import com.mapmory.shared.presentation.map.domain.GeoPoint

data class MapFocusRequest(
    val id: Int,
    val point: GeoPoint,
)

data class CurrentLocationActions(
    val isLoading: Boolean,
    val request: () -> Unit,
)

@Composable
expect fun rememberCurrentLocationActions(
    onLocation: (GeoPoint) -> Unit,
    onError: (String) -> Unit,
): CurrentLocationActions
