@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.presentation.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.mapmory.shared.presentation.map.domain.GeoPoint
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLErrorDenied
import platform.CoreLocation.kCLErrorLocationUnknown
import platform.CoreLocation.kCLErrorNetwork
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSinceNow
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun rememberCurrentLocationActions(
    onLocation: (GeoPoint) -> Unit,
    onError: (String) -> Unit,
    cachedLocationMaxAgeMillis: Long,
): CurrentLocationActions {
    val latestLocation = rememberUpdatedState(onLocation)
    val latestError = rememberUpdatedState(onError)
    var showPermissionSettings by remember { mutableStateOf(false) }
    val controller = remember(cachedLocationMaxAgeMillis) {
        IosCurrentLocationController(
            onLocation = { latestLocation.value(it) },
            onError = { latestError.value(it) },
            onPermissionRequired = { showPermissionSettings = true },
            cachedLocationMaxAgeMillis = cachedLocationMaxAgeMillis,
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    if (showPermissionSettings) {
        LocationSettingsDialog(
            onDismiss = { showPermissionSettings = false },
            onOpenSettings = {
                showPermissionSettings = false
                val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
                if (url != null) {
                    UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>()) { opened ->
                        if (!opened) latestError.value("앱 설정을 열지 못했어요. 기기 설정에서 Mapmory의 위치 권한을 허용해 주세요.")
                    }
                } else {
                    latestError.value("앱 설정을 열지 못했어요. 기기 설정에서 Mapmory의 위치 권한을 허용해 주세요.")
                }
            },
        )
    }
    return CurrentLocationActions(controller.isLoading, controller::request) {
        showPermissionSettings = false
        controller.cancel()
    }
}

private class IosCurrentLocationController(
    private val onLocation: (GeoPoint) -> Unit,
    private val onError: (String) -> Unit,
    private val onPermissionRequired: () -> Unit,
    private val cachedLocationMaxAgeMillis: Long,
) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var timeout: Job? = null
    var isLoading by mutableStateOf(false)
        private set
    private var waitingForPermission = false

    init {
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    fun request() {
        if (isLoading) return
        isLoading = true
        if (!CLLocationManager.locationServicesEnabled()) {
            fail("기기의 위치 서비스가 꺼져 있어요. 설정 앱의 개인정보 보호 및 보안에서 위치 서비스를 켜 주세요.")
            return
        }
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> requestLocation()
            kCLAuthorizationStatusNotDetermined -> {
                waitingForPermission = true
                manager.requestWhenInUseAuthorization()
            }
            kCLAuthorizationStatusDenied -> {
                handlePermissionDenied()
            }
            kCLAuthorizationStatusRestricted -> fail("이 기기에서는 위치 권한을 변경할 수 없어요. 기기 설정 제한을 확인하거나 장소를 직접 검색해 주세요.")
            else -> fail("현재 위치 권한이 필요해요. 설정에서 권한을 허용하거나 장소를 직접 검색해 주세요.")
        }
    }

    fun dispose() {
        cancel()
        manager.delegate = null
        scope.cancel()
    }

    fun cancel() {
        timeout?.cancel()
        timeout = null
        waitingForPermission = false
        isLoading = false
        manager.stopUpdatingLocation()
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (!waitingForPermission) return
        waitingForPermission = false
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> requestLocation()
            kCLAuthorizationStatusDenied -> handlePermissionDenied()
            kCLAuthorizationStatusRestricted -> fail("이 기기에서는 위치 권한을 변경할 수 없어요. 기기 설정 제한을 확인하거나 장소를 직접 검색해 주세요.")
            else -> fail("현재 위치 권한이 필요해요. 설정에서 권한을 허용하거나 장소를 직접 검색해 주세요.")
        }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        if (!isLoading) return
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        cancel()
        if (location == null || location.horizontalAccuracy < 0) {
            onError("현재 위치를 가져오지 못했어요. 위치 서비스와 권한을 확인하거나 장소를 직접 검색해 주세요.")
            return
        }
        location.coordinate.useContents {
            onLocation(GeoPoint(longitude.toFloat(), latitude.toFloat()))
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        if (!isLoading) return
        when (didFailWithError.code) {
            kCLErrorDenied -> handlePermissionDenied()
            kCLErrorNetwork -> fail("네트워크를 사용할 수 없어 위치를 확인하지 못했어요. 연결을 확인한 뒤 다시 시도하거나 장소를 직접 검색해 주세요.")
            kCLErrorLocationUnknown -> fail("현재 위치 신호를 아직 찾지 못했어요. 잠시 뒤 다시 시도하거나 장소를 직접 검색해 주세요.")
            else -> fail("현재 위치를 가져오지 못했어요. 위치 서비스와 권한을 확인하거나 장소를 직접 검색해 주세요.")
        }
    }

    private fun requestLocation() {
        val cached = manager.location
        val cacheAgeSeconds = cachedLocationMaxAgeMillis / 1_000.0
        if (cached != null && cached.horizontalAccuracy >= 0 && cached.timestamp.timeIntervalSinceNow in -cacheAgeSeconds..0.0) {
            locationManager(manager, listOf(cached))
            return
        }
        timeout?.cancel()
        timeout = scope.launch {
            delay(15_000L)
            fail("위치 확인이 지연되고 있어요. 실내에서는 GPS 신호가 약할 수 있어요. 네트워크와 위치 서비스를 확인하거나 장소를 직접 검색해 주세요.")
        }
        manager.requestLocation()
    }

    private fun fail(message: String) {
        cancel()
        onError(message)
    }

    private fun handlePermissionDenied() {
        cancel()
        if (CLLocationManager.locationServicesEnabled()) {
            onPermissionRequired()
        } else {
            onError("기기의 위치 서비스가 꺼져 있어요. 설정 앱의 개인정보 보호 및 보안에서 위치 서비스를 켜 주세요.")
        }
    }
}
