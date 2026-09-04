package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.app.createGuestRemoteAppContainer
import com.mapmory.shared.data.auth.IosAuthTokenStore
import com.mapmory.shared.data.media.IosPhotoPreviewCache
import com.mapmory.shared.data.repository.IosMapSummaryCache
import com.mapmory.shared.data.repository.IosTripStatisticsCache
import com.mapmory.shared.data.settings.IosThemePreference

fun MainViewController(
    onThemeChanged: (Boolean) -> Unit,
    analytics: MapmoryAnalytics,
) = createGuestRemoteAppContainer(
    tokenStore = IosAuthTokenStore(),
    photoPreviewCache = IosPhotoPreviewCache(),
    mapSummaryCache = IosMapSummaryCache(),
    tripStatisticsCache = IosTripStatisticsCache(),
    themePreference = IosThemePreference(),
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
