package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.NoOpMapmoryAnalytics
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.navigation.MapmoryNavHost
import com.mapmory.shared.navigation.MapmoryNavigator
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.presentation.triprecord.screen.ProvideTripRecordPalettes

@Composable
fun MapmoryApp(
    container: AppContainer? = null,
    navigation: MapmoryNavigation? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    initialIsDarkTheme: Boolean = false,
    onThemeChanged: (Boolean) -> Unit = {},
    analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
) {
    var isDarkTheme by rememberSaveable { mutableStateOf(initialIsDarkTheme) }
    val latestOnThemeChanged by rememberUpdatedState(onThemeChanged)
    val themeState = remember(isDarkTheme) {
        MapmoryThemeState(
            isDark = isDarkTheme,
            onThemeChange = { shouldUseDarkTheme ->
                isDarkTheme = shouldUseDarkTheme
                latestOnThemeChanged(shouldUseDarkTheme)
            },
        )
    }
    val ownedContainer = remember(container) {
        if (container == null) createInMemoryAppContainer() else null
    }
    val appContainer = requireNotNull(container ?: ownedContainer)
    val navController = rememberNavController()
    val navigator = remember(navController) { MapmoryNavigator(navController) }
    val backHandlerRegistry = remember { MapmoryBackHandlerRegistry() }
    val latestNavigateBack = rememberUpdatedState {
        backHandlerRegistry.handleBack() || navigator.navigateBack()
    }

    DisposableEffect(navigation, navigator, backHandlerRegistry) {
        navigation?.bindBackHandler { latestNavigateBack.value() }
        onDispose { navigation?.unbindBackHandler() }
    }
    DisposableEffect(ownedContainer) {
        onDispose { ownedContainer?.close() }
    }

    CompositionLocalProvider(
        LocalMapmoryTheme provides themeState,
        LocalMapmoryAnalytics provides analytics,
    ) {
        ProvideTripRecordPalettes(isDark = isDarkTheme) {
            MapmoryNavHost(
                navController = navController,
                navigator = navigator,
                container = appContainer,
                backHandlerRegistry = backHandlerRegistry,
                contentWindowInsets = contentWindowInsets,
            )
        }
    }
}

@Preview(
    name = "앱 지도",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun MapmoryAppPreview() {
    PreviewSurface { MapmoryApp() }
}
