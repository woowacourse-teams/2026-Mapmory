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
    cachedLocationMaxAgeMillis: Long = DefaultCurrentLocationCacheMaxAgeMillis,
): CurrentLocationActions

internal const val DefaultCurrentLocationCacheMaxAgeMillis = 60_000L
internal const val MapCurrentLocationCacheMaxAgeMillis = 120_000L

internal fun isRecentCurrentLocation(
    nowNanos: Long,
    locationNanos: Long,
    maxAgeNanos: Long = DefaultCurrentLocationCacheMaxAgeMillis * 1_000_000L,
): Boolean = locationNanos > 0 && nowNanos - locationNanos in 0..maxAgeNanos
