package com.mapmory.shared.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    val latestLocation = rememberUpdatedState(onLocation)
    val latestError = rememberUpdatedState(onError)
    var isLoading by remember { mutableStateOf(false) }
    var requestId by remember { mutableStateOf(0) }
    var permissionRequestId by remember { mutableStateOf(0) }
    var cancellationSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    var pendingListener by remember { mutableStateOf<LocationListener?>(null) }
    var timeout by remember { mutableStateOf<Runnable?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    fun clearPendingRequest() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        cancellationSignal?.cancel()
        cancellationSignal = null
        pendingListener?.let(manager::removeUpdates)
        pendingListener = null
    }

    fun finish(id: Int, location: Location?, error: String? = null) {
        if (!isLoading || id != requestId) return
        isLoading = false
        clearPendingRequest()
        if (location == null) {
            latestError.value(
                error ?: "현재 위치를 가져오지 못했어요. 위치 서비스와 권한을 확인하거나 장소를 직접 검색해 주세요.",
            )
        } else {
            latestLocation.value(GeoPoint(location.longitude.toFloat(), location.latitude.toFloat()))
        }
    }

    lateinit var requestLocation: (Int) -> Unit
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val id = permissionRequestId
        if (isLoading && id == requestId) {
            if (granted) {
                requestLocation(id)
            } else {
                finish(id, null, "현재 위치를 보려면 위치 권한이 필요해요. 권한을 허용하거나 장소를 직접 검색해 주세요.")
            }
        }
    }

    requestLocation = { id ->
        try {
            @Suppress("DEPRECATION")
            val provider = manager.getBestProvider(
                Criteria().apply {
                    accuracy = Criteria.ACCURACY_COARSE
                    powerRequirement = Criteria.POWER_LOW
                },
                true,
            )
            if (provider == null) {
                finish(id, null, "기기의 위치 제공자를 사용할 수 없어요. 위치 서비스를 켜거나 장소를 직접 검색해 주세요.")
            } else {
                timeout = Runnable {
                    finish(id, null, "현재 위치 확인이 지연되고 있어요. 위치 서비스와 권한을 확인하거나 장소를 직접 검색해 주세요.")
                }.also { handler.postDelayed(it, LocationRequestTimeoutMillis) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cancellationSignal = signal
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { finish(id, it) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) = finish(id, location)
                    }
                    pendingListener = listener
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        } catch (_: SecurityException) {
            finish(id, null, "현재 위치 권한이 필요해요. 권한을 허용하거나 장소를 직접 검색해 주세요.")
        } catch (_: IllegalArgumentException) {
            finish(id, null, "현재 위치를 확인할 수 없어요. 위치 서비스를 확인하거나 장소를 직접 검색해 주세요.")
        }
    }

    fun cancel() {
        requestId += 1
        isLoading = false
        clearPendingRequest()
    }

    DisposableEffect(manager) {
        onDispose { cancel() }
    }

    return CurrentLocationActions(
        isLoading = isLoading,
        request = {
            if (!isLoading) {
                requestId += 1
                isLoading = true
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    requestLocation(requestId)
                } else {
                    permissionRequestId = requestId
                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        },
        cancel = ::cancel,
    )
}

private const val LocationRequestTimeoutMillis = 15_000L
