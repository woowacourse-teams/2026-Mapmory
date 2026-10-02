package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.app.createGuestRemoteAppContainer
import com.mapmory.shared.app.IosBackgroundSaveExecution
import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.media.IosLocalPhotoDataSource
import com.mapmory.shared.data.media.IosPhotoPreviewCache
import com.mapmory.shared.data.repository.IosMapSummaryCache
import com.mapmory.shared.data.repository.IosTripStatisticsCache
import com.mapmory.shared.data.settings.IosOnboardingPreference
import com.mapmory.shared.data.settings.IosThemePreference
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
fun MainViewController(
    apiBaseUrl: String,
    onThemeChanged: (Boolean) -> Unit,
    analytics: MapmoryAnalytics,
    tokenStore: AuthTokenStore,
) = createGuestRemoteAppContainer(
    apiBaseUrl = apiBaseUrl,
    tokenStore = tokenStore,
    photoPreviewCache = IosPhotoPreviewCache(),
    localPhotoDataSource = IosLocalPhotoDataSource(),
    backgroundSaveExecution = IosBackgroundSaveExecution(),
    mapSummaryCache = IosMapSummaryCache(),
    tripStatisticsCache = IosTripStatisticsCache(),
    themePreference = IosThemePreference(),
    onboardingPreference = IosOnboardingPreference(),
    onAuthRefreshFailed = { stage, error ->
        if (!Platform.isDebugBinary) {
            analytics.logEvent(
                MapmoryAnalyticsEvent.AUTH_SESSION_REFRESH_FAILED,
                mapOf(
                    "platform" to "ios",
                    "stage" to stage,
                    "error_type" to (error::class.simpleName ?: "unknown"),
                ),
            )
        }
    },
).let { container ->
    ComposeUIViewController {
        DisposableEffect(container) {
            onDispose(container::close)
        }

        MapmoryApp(
            container = container,
            contentWindowInsets = WindowInsets.safeDrawing,
            onThemeChanged = onThemeChanged,
            analytics = analytics,
        )
    }
}
