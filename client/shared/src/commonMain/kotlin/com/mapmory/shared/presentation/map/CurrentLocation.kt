package com.mapmory.shared.presentation.map

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
internal fun LocationSettingsDialog(
    title: String = "위치 권한이 필요해요",
    message: String = "현재 위치를 사용하려면 앱 설정에서 위치 권한을 허용해 주세요. 허용한 뒤 현재 위치 버튼을 다시 눌러 주세요.",
    openSettingsLabel: String = "설정으로 이동",
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(openSettingsLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에") }
        },
    )
}

internal const val DefaultCurrentLocationCacheMaxAgeMillis = 60_000L
internal const val MapCurrentLocationCacheMaxAgeMillis = 120_000L

internal fun isRecentCurrentLocation(
    nowNanos: Long,
    locationNanos: Long,
    maxAgeNanos: Long = DefaultCurrentLocationCacheMaxAgeMillis * 1_000_000L,
): Boolean = locationNanos > 0 && nowNanos - locationNanos in 0..maxAgeNanos
