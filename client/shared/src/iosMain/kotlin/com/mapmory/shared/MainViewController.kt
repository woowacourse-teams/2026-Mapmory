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

private var iosContainer: com.mapmory.shared.app.AppContainer? = null
private var iosAnalytics: MapmoryAnalytics = com.mapmory.shared.analytics.NoOpMapmoryAnalytics

/** Bootstrap the same queue for a system relaunch, even when SwiftUI has no visible window. */
fun resumeBackgroundUploads(apiBaseUrl: String, tokenStore: AuthTokenStore, analytics: MapmoryAnalytics) {
    iosAnalytics = analytics
    getIosContainer(apiBaseUrl, tokenStore)
}

@OptIn(ExperimentalNativeApi::class)
private fun getIosContainer(apiBaseUrl: String, tokenStore: AuthTokenStore): com.mapmory.shared.app.AppContainer {
    iosContainer?.let { return it }
    return createGuestRemoteAppContainer(
        apiBaseUrl = apiBaseUrl,
        tokenStore = tokenStore,
        photoPreviewCache = IosPhotoPreviewCache(),
        localPhotoDataSource = IosLocalPhotoDataSource(),
        backgroundSaveExecution = IosBackgroundSaveExecution(apiBaseUrl),
        mapSummaryCache = IosMapSummaryCache(),
        tripStatisticsCache = IosTripStatisticsCache(),
        themePreference = IosThemePreference(),
        onboardingPreference = IosOnboardingPreference(),
        teamDevicePreference = IosTeamDevicePreference(),
        shareVideoJobStore = IosShareVideoJobStore(apiBaseUrl),
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
    ).also { iosContainer = it }
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
            showTimelineLayoutSwitcher = Platform.isDebugBinary,
        )
        }
    }
}
