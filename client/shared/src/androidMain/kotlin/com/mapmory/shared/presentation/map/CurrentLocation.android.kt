package com.mapmory.shared.presentation.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.core.location.LocationManagerCompat
import com.mapmory.shared.presentation.map.domain.GeoPoint

@Composable
actual fun rememberCurrentLocationActions(
    onLocation: (GeoPoint) -> Unit,
    onError: (String) -> Unit,
    cachedLocationMaxAgeMillis: Long,
): CurrentLocationActions {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val latestLocation = rememberUpdatedState(onLocation)
    val latestError = rememberUpdatedState(onError)
    var isLoading by remember { mutableStateOf(false) }
    var requestId by remember { mutableStateOf(0) }
    var permissionRequestId by remember { mutableStateOf(0) }
    val cancellationSignals = remember { mutableListOf<CancellationSignal>() }
    var timeout by remember { mutableStateOf<Runnable?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val isEmulator = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"

    fun clearPendingRequest() {
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val signals = cancellationSignals.toList()
        cancellationSignals.clear()
        signals.forEach { it.cancel() }
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
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val id = permissionRequestId
        if (isLoading && id == requestId) {
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                requestLocation(id)
            } else {
                finish(id, null, "현재 위치를 보려면 위치 권한이 필요해요. 권한을 허용하거나 장소를 직접 검색해 주세요.")
            }
        }
    }

    requestLocation = requestLocation@{ id ->
        try {
            val providers = manager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
            if (!LocationManagerCompat.isLocationEnabled(manager) || providers.isEmpty()) {
                finish(id, null, "기기의 위치 제공자를 사용할 수 없어요. 위치 서비스를 켜거나 장소를 직접 검색해 주세요.")
            } else {
                val now = SystemClock.elapsedRealtimeNanos()
                val recentLocation = providers.mapNotNull { provider ->
                    try {
                        manager.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                }.filter {
                    isRecentCurrentLocation(
                        nowNanos = now,
                        locationNanos = it.elapsedRealtimeNanos,
                        maxAgeNanos = cachedLocationMaxAgeMillis * 1_000_000L,
                    )
                }
                    .maxByOrNull { it.elapsedRealtimeNanos }
                if (recentLocation != null) {
                    finish(id, recentLocation)
                    return@requestLocation
                }
                timeout = Runnable {
                    finish(
                        id,
                        null,
                        if (isEmulator) "에뮬레이터에 모의 위치를 설정한 뒤 다시 눌러 주세요. 위치를 설정할 수 없다면 장소를 직접 검색해 주세요."
                        else "현재 위치 확인이 지연되고 있어요. 위치 서비스와 권한을 확인하거나 장소를 직접 검색해 주세요.",
                    )
                }.also { handler.postDelayed(it, LocationRequestTimeoutMillis) }
                val pendingProviders = providers.toMutableSet()
                fun onResult(provider: String, location: Location?) {
                    if (!isLoading || id != requestId) return
                    pendingProviders.remove(provider)
                    if (location != null || pendingProviders.isEmpty()) finish(id, location)
                }
                // 한 제공자가 실패해도 다른 제공자의 현재 위치 결과를 기다린다.
                for (provider in providers) {
                    if (!isLoading || id != requestId) break
                    val signal = CancellationSignal()
                    cancellationSignals.add(signal)
                    try {
                        LocationManagerCompat.getCurrentLocation(
                            manager,
                            provider,
                            signal,
                            { command -> handler.post(command) },
                        ) { onResult(provider, it) }
                    } catch (_: SecurityException) {
                        onResult(provider, null)
                    } catch (_: IllegalArgumentException) {
                        onResult(provider, null)
                    }
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
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                }
            }
        },
        cancel = ::cancel,
    )
}

private const val LocationRequestTimeoutMillis = 15_000L
