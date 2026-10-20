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
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSinceNow
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
): CurrentLocationActions {
    val latestLocation = rememberUpdatedState(onLocation)
    val latestError = rememberUpdatedState(onError)
    val controller = remember {
        IosCurrentLocationController(
            onLocation = { latestLocation.value(it) },
            onError = { latestError.value(it) },
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    return CurrentLocationActions(controller.isLoading, controller::request, controller::cancel)
}

private class IosCurrentLocationController(
    private val onLocation: (GeoPoint) -> Unit,
    private val onError: (String) -> Unit,
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
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> requestLocation()
            kCLAuthorizationStatusNotDetermined -> {
                waitingForPermission = true
                manager.requestWhenInUseAuthorization()
            }
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
        if (isLoading) fail("현재 위치를 가져오지 못했어요. 위치 서비스와 권한을 확인하거나 장소를 직접 검색해 주세요.")
    }

    private fun requestLocation() {
        val cached = manager.location
        if (cached != null && cached.horizontalAccuracy >= 0 && cached.timestamp.timeIntervalSinceNow in -60.0..0.0) {
            locationManager(manager, listOf(cached))
            return
        }
        timeout?.cancel()
        timeout = scope.launch {
            delay(15_000L)
            fail("현재 위치 확인이 지연되고 있어요. 위치 서비스를 확인하거나 장소를 직접 검색해 주세요.")
        }
        manager.requestLocation()
    }

    private fun fail(message: String) {
        cancel()
        onError(message)
    }
}
