package com.mapmory.shared.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mapmory.shared.presentation.map.domain.GeoPoint

@Composable
actual fun rememberCurrentLocationActions(
    onLocation: (GeoPoint) -> Unit,
    onError: (String) -> Unit,
): CurrentLocationActions {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val latestLocation by rememberUpdatedState(onLocation)
    val latestError by rememberUpdatedState(onError)
    var isLoading by remember { mutableStateOf(false) }

    fun finish(location: android.location.Location?) {
        isLoading = false
        if (location == null) {
            latestError("현재 위치를 가져오지 못했습니다.")
        } else {
            latestLocation(GeoPoint(location.longitude.toFloat(), location.latitude.toFloat()))
        }
    }

    fun requestLocation() {
        try {
            if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                isLoading = false
                latestError("위치 서비스를 켜주세요.")
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor, ::finish)
            } else {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, { finish(it) }, Looper.getMainLooper())
            }
        } catch (_: SecurityException) {
            isLoading = false
            latestError("위치 권한을 허용해주세요.")
        } catch (_: IllegalArgumentException) {
            isLoading = false
            latestError("현재 위치를 확인할 수 없습니다.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) requestLocation() else {
            isLoading = false
            latestError("현재 위치를 보려면 위치 권한이 필요합니다.")
        }
    }

    return CurrentLocationActions(
        isLoading = isLoading,
        request = {
            if (!isLoading) {
                isLoading = true
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestLocation()
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        },
    )
}
