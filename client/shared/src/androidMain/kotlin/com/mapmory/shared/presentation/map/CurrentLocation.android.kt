package com.mapmory.shared.presentation.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
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
import androidx.core.app.ActivityCompat
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
    val activity = LocalActivity.current
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val latestLocation = rememberUpdatedState(onLocation)
    val latestError = rememberUpdatedState(onError)
    var isLoading by remember { mutableStateOf(false) }
    var requestId by remember { mutableStateOf(0) }
    var permissionRequestId by remember { mutableStateOf(0) }
    var showPermissionSettings by remember { mutableStateOf(false) }
    var showLocationServicesSettings by remember { mutableStateOf(false) }
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

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun showSettingsIfPermissionWasPermanentlyDenied() {
        val hasRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION)
        } ?: true
        if (!hasLocationPermission() && !hasRationale) showPermissionSettings = true
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
                showSettingsIfPermissionWasPermanentlyDenied()
            }
        }
    }

    requestLocation = requestLocation@{ id ->
        try {
            val providers = manager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
            if (!LocationManagerCompat.isLocationEnabled(manager) || providers.isEmpty()) {
                finish(id, null, "기기의 위치 제공자를 사용할 수 없어요. 위치 서비스를 켜거나 장소를 직접 검색해 주세요.")
                showLocationServicesSettings = true
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
                        else "위치 확인이 지연되고 있어요. 실내에서는 GPS 신호가 약하거나 네트워크 위치를 사용할 수 없어요. 연결과 위치 서비스를 확인하거나 장소를 직접 검색해 주세요.",
                    )
                }.also { handler.postDelayed(it, LocationRequestTimeoutMillis) }
                val pendingProviders = providers.toMutableSet()
                fun onResult(provider: String, location: Location?) {
                    if (!isLoading || id != requestId) return
                    pendingProviders.remove(provider)
                    if (location != null) {
                        finish(id, location)
                    } else if (pendingProviders.isEmpty()) {
                        finish(
                            id,
                            null,
                            if (isEmulator) "에뮬레이터에 모의 위치를 설정한 뒤 다시 눌러 주세요. 장소를 직접 검색할 수도 있어요."
                            else "현재 위치를 찾지 못했어요. GPS 신호나 네트워크 연결이 불안정할 수 있어요. 위치와 연결을 확인한 뒤 다시 시도해 주세요.",
                        )
                    }
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
                        if (hasLocationPermission()) onResult(provider, null) else {
                            finish(id, null, "현재 위치 권한이 해제되었어요. 권한을 허용하거나 장소를 직접 검색해 주세요.")
                            showSettingsIfPermissionWasPermanentlyDenied()
                        }
                    } catch (_: IllegalArgumentException) {
                        onResult(provider, null)
                    }
                }
            }
        } catch (_: SecurityException) {
            finish(id, null, "현재 위치 권한이 필요해요. 권한을 허용하거나 장소를 직접 검색해 주세요.")
            showSettingsIfPermissionWasPermanentlyDenied()
        } catch (_: IllegalArgumentException) {
            finish(id, null, "현재 위치를 확인할 수 없어요. 위치 서비스를 확인하거나 장소를 직접 검색해 주세요.")
        }
    }

    fun cancel() {
        requestId += 1
        isLoading = false
        clearPendingRequest()
        showPermissionSettings = false
        showLocationServicesSettings = false
    }

    DisposableEffect(manager) {
        onDispose { cancel() }
    }

    if (showPermissionSettings) {
        LocationSettingsDialog(
            onDismiss = { showPermissionSettings = false },
            onOpenSettings = {
                showPermissionSettings = false
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: Exception) {
                    latestError.value("앱 설정을 열지 못했어요. 기기 설정에서 Mapmory의 위치 권한을 허용해 주세요.")
                }
            },
        )
    }
    if (showLocationServicesSettings) {
        LocationSettingsDialog(
            title = "위치 서비스가 꺼져 있어요",
            message = "기기의 위치 서비스를 켜야 현재 위치를 확인할 수 있어요. 켠 뒤 현재 위치 버튼을 다시 눌러 주세요.",
            openSettingsLabel = "위치 설정 열기",
            onDismiss = { showLocationServicesSettings = false },
            onOpenSettings = {
                showLocationServicesSettings = false
                try {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    latestError.value("위치 설정을 열지 못했어요. 빠른 설정에서 위치를 켜 주세요.")
                }
            },
        )
    }

    return CurrentLocationActions(
        isLoading = isLoading,
        request = {
            if (!isLoading) {
                requestId += 1
                isLoading = true
                if (hasLocationPermission()) {
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
