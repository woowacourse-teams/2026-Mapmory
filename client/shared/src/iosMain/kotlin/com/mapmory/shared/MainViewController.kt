package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.ComposeUIViewController
import com.mapmory.shared.app.createGuestRemoteAppContainer
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.data.auth.IosAuthTokenStore
import com.mapmory.shared.data.media.IosPhotoPreviewCache

fun MainViewController(
    onThemeChanged: (Boolean) -> Unit,
    analytics: MapmoryAnalytics,
) = createGuestRemoteAppContainer(
    tokenStore = IosAuthTokenStore(),
    photoPreviewCache = IosPhotoPreviewCache(),
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
