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
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlinx.cinterop.useContents

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
    return CurrentLocationActions(controller.isLoading, controller::request)
}

private class IosCurrentLocationController(
    private val onLocation: (GeoPoint) -> Unit,
    private val onError: (String) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    var isLoading by mutableStateOf(false)
        private set
    private var waitingForPermission = false

    init {
        manager.delegate = this
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    fun request() {
        if (isLoading) return
        isLoading = true
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            kCLAuthorizationStatusNotDetermined -> {
                waitingForPermission = true
                manager.requestWhenInUseAuthorization()
            }
            else -> fail("현재 위치를 보려면 위치 권한이 필요합니다.")
        }
    }

    fun dispose() {
        manager.stopUpdatingLocation()
        manager.delegate = null
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (!waitingForPermission) return
        waitingForPermission = false
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse,
            kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            else -> fail("현재 위치를 보려면 위치 권한이 필요합니다.")
        }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        isLoading = false
        if (location == null) {
            onError("현재 위치를 가져오지 못했습니다.")
            return
        }
        location.coordinate.useContents {
            onLocation(GeoPoint(longitude.toFloat(), latitude.toFloat()))
        }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        fail("현재 위치를 가져오지 못했습니다.")
    }

    private fun fail(message: String) {
        isLoading = false
        waitingForPermission = false
        onError(message)
    }
}
