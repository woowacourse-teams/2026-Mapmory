package com.mapmory.shared.presentation.map

import androidx.compose.runtime.Composable
import com.mapmory.shared.presentation.map.domain.GeoPoint

@Composable
actual fun rememberCurrentLocationActions(
    onLocation: (GeoPoint) -> Unit,
    onError: (String) -> Unit,
    cachedLocationMaxAgeMillis: Long,
): CurrentLocationActions = CurrentLocationActions(
    isLoading = false,
    request = { onError("현재 위치는 모바일 앱에서만 사용할 수 있습니다.") },
    cancel = {},
)
