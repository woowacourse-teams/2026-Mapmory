@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.uikit.EndEdgePanGestureBehavior
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import kotlin.native.Platform
import com.mapmory.shared.app.createGuestRemoteAppContainer
import com.mapmory.shared.app.IosBackgroundSaveExecution
import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.media.configureIosPhotoImageLoader
import com.mapmory.shared.data.media.IosLocalPhotoDataSource
import com.mapmory.shared.data.media.IosPhotoPreviewCache
import com.mapmory.shared.data.repository.IosMapSummaryCache
import com.mapmory.shared.data.repository.IosTripStatisticsCache
import com.mapmory.shared.data.settings.IosOnboardingPreference
import com.mapmory.shared.data.settings.IosShareVideoJobStore
import com.mapmory.shared.data.settings.IosTeamDevicePreference
import com.mapmory.shared.data.settings.IosThemePreference
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.logging.mapmoryDebugLog
import kotlin.experimental.ExperimentalNativeApi
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private var iosContainer: com.mapmory.shared.app.AppContainer? = null
private var iosAnalytics: MapmoryAnalytics = com.mapmory.shared.analytics.NoOpMapmoryAnalytics

private var iosPushToken: String? = null
private var iosNotificationNavigation: MapmoryNavigation? = null
private var pendingIosNotification: (() -> Unit)? = null

fun updateIosPushToken(token: String?) {
    platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
        iosPushToken = token
        refreshIosPushPermission()
    }
}

fun refreshIosPushPermission() = refreshIosPushPermission { }

internal suspend fun awaitIosPushPermissionRefresh() {
    suspendCancellableCoroutine<Unit> { continuation ->
        refreshIosPushPermission {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}

private fun refreshIosPushPermission(onRefreshed: () -> Unit) {
    platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()
        .getNotificationSettingsWithCompletionHandler { settings ->
            val permissionGranted = settings?.authorizationStatus in listOf(
                platform.UserNotifications.UNAuthorizationStatusAuthorized,
                platform.UserNotifications.UNAuthorizationStatusProvisional,
                platform.UserNotifications.UNAuthorizationStatusEphemeral,
            )
            platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                iosContainer?.pushTokenRegistrationManager?.updateDeviceState(
                    token = iosPushToken,
                    notificationPermissionGranted = permissionGranted,
                    photoEnabled = com.mapmory.shared.data.settings.isPhotoRecordCompletionNotificationEnabled(),
                    videoEnabled = com.mapmory.shared.data.settings.isTravelVideoCompletionNotificationEnabled(),
                )
                onRefreshed()
            }
        }
}

fun receiveIosPushNotification(type: String, recordId: String?, jobId: String?, preserveBackStack: Boolean) {
    val preserve = preserveBackStack && iosNotificationNavigation != null
    val action: () -> Unit = when (type) {
        "PHOTO_RECORD_COMPLETED" -> {
            val id = recordId?.toLongOrNull()?.takeIf { it > 0 } ?: return
            fun() { iosNotificationNavigation?.openRecordNotification(id, preserve) }
        }
        "VIDEO_COMPLETED" -> {
            val id = jobId?.toLongOrNull()?.takeIf { it > 0 } ?: return
            fun() { iosNotificationNavigation?.openVideoNotification(id, preserve) }
        }
        else -> return
    }
    if (iosNotificationNavigation == null) pendingIosNotification = action else action()
}

internal fun bindIosNotificationNavigation(navigation: MapmoryNavigation?) {
    iosNotificationNavigation = navigation
    if (navigation != null) {
        pendingIosNotification?.invoke()
        pendingIosNotification = null
    }
}

/** Bootstrap the same queue for a system relaunch, even when SwiftUI has no visible window. */
fun resumeBackgroundUploads(apiBaseUrl: String, tokenStore: AuthTokenStore, analytics: MapmoryAnalytics) {
    iosAnalytics = analytics
    getIosContainer(apiBaseUrl, tokenStore)
}

@OptIn(ExperimentalNativeApi::class)
private fun getIosContainer(apiBaseUrl: String, tokenStore: AuthTokenStore): com.mapmory.shared.app.AppContainer {
    iosContainer?.let { return it }
    // One instance: saves read originals through the same source that prefetched them.
    val localPhotos = IosLocalPhotoDataSource()
    // The saved-photo preview writer must replace entries in the same cache the app reads.
    val photoPreviews = IosPhotoPreviewCache()
    return createGuestRemoteAppContainer(
        apiBaseUrl = apiBaseUrl,
        tokenStore = tokenStore,
        photoPreviewCache = photoPreviews,
        localPhotoDataSource = localPhotos,
        originalPhotoPrefetcher = localPhotos,
        savedPhotoPreviewWriter = com.mapmory.shared.data.media.IosSavedPhotoPreviewWriter(photoPreviews),
        backgroundSaveExecution = IosBackgroundSaveExecution(apiBaseUrl),
        mapSummaryCache = IosMapSummaryCache(),
        tripStatisticsCache = IosTripStatisticsCache(),
        themePreference = IosThemePreference(),
        onboardingPreference = IosOnboardingPreference(),
        teamDevicePreference = IosTeamDevicePreference(),
        shareVideoJobStore = IosShareVideoJobStore(apiBaseUrl),
        pushTokenRegistrationStore = com.mapmory.shared.data.settings.IosPushTokenRegistrationStore(apiBaseUrl),
        pushTokenPlatform = "IOS",
        albumShareStore = com.mapmory.shared.data.settings.IosAlbumShareStore(apiBaseUrl),
        onAuthRefreshFailed = { stage, error ->
            if (Platform.isDebugBinary) {
                val reason = (error as? MapmoryApiException)
                    ?.let { "HTTP ${it.statusCode} ${it.code}" }
                    ?: (error::class.simpleName ?: "unknown")
                mapmoryDebugLog("MapmoryAuth", "refresh failed stage=$stage: $reason")
            } else {
                iosAnalytics.logEvent(
                    MapmoryAnalyticsEvent.AUTH_SESSION_REFRESH_FAILED,
                    mapOf(
                        "platform" to "ios",
                        "stage" to stage,
                        "error_type" to (error::class.simpleName ?: "unknown"),
                    ),
                )
            }
        },
        // 컨테이너가 MainViewController보다 먼저 만들어질 수 있어 호출 시점의 로거로 보낸다.
        analytics = object : MapmoryAnalytics {
            override fun logEvent(name: String, parameters: Map<String, String>) =
                iosAnalytics.logEvent(name, parameters)

            override fun setUserProperty(name: String, value: String?) =
                iosAnalytics.setUserProperty(name, value)
        },
    ).also {
        iosContainer = it
        refreshIosPushPermission()
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalNativeApi::class)
fun MainViewController(
    apiBaseUrl: String,
    onThemeChanged: (Boolean) -> Unit,
    analytics: MapmoryAnalytics,
    tokenStore: AuthTokenStore,
    navigation: MapmoryNavigation,
    updateState: com.mapmory.shared.update.ForceUpdateState,
    onRetryUpdateCheck: () -> Unit,
    socialLoginLauncher: com.mapmory.shared.presentation.auth.SocialLoginLauncher,
): platform.UIKit.UIViewController {
    bindIosNotificationNavigation(navigation)
    configureIosPhotoImageLoader()
    iosAnalytics = analytics
    val container = getIosContainer(apiBaseUrl, tokenStore)
    return ComposeUIViewController(configure = {
        endEdgePanGestureBehavior = EndEdgePanGestureBehavior.Forward
    }) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.mapmory.shared.presentation.auth.LocalSocialLoginLauncher provides socialLoginLauncher,
        ) {
        MapmoryApp(
            container = container,
            navigation = navigation,
            updateState = updateState,
            onRetryUpdateCheck = onRetryUpdateCheck,
            contentWindowInsets = WindowInsets.safeDrawing,
            onThemeChanged = onThemeChanged,
            analytics = analytics,
        )
        }
    }
}
