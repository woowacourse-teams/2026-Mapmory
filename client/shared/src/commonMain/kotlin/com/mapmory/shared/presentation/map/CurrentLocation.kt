package com.mapmory.shared.presentation.map

import androidx.compose.runtime.Composable
import com.mapmory.shared.presentation.map.domain.GeoPoint

data class CurrentLocationActions(
    val isLoading: Boolean,
    val request: () -> Unit,
    val cancel: () -> Unit,
)

@Composable
expect fun rememberCurrentLocationActions(
    onLocation: (GeoPoint) -> Unit,
    onError: (String) -> Unit,
): CurrentLocationActions

internal fun isRecentCurrentLocation(nowNanos: Long, locationNanos: Long): Boolean =
    locationNanos > 0 && nowNanos - locationNanos in 0..60_000_000_000L
