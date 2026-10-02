package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.mapmory.shared.presentation.splash.MapmorySplashScreen
import com.mapmory.shared.presentation.triprecord.screen.ProvideTripRecordPalettes
import kotlinx.coroutines.delay

@Composable
fun MapmoryApp(
    container: AppContainer? = null,
    navigation: MapmoryNavigation? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    onThemeChanged: (Boolean) -> Unit = {},
    analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
) {
    val ownedContainer = remember(container) {
        if (container == null) createInMemoryAppContainer() else null
    }
    val appContainer = requireNotNull(container ?: ownedContainer)
    val themePreference = appContainer.themePreference
    var isDarkTheme by remember(themePreference) {
        mutableStateOf(themePreference.loadIsDarkTheme())
    }
    val latestOnThemeChanged by rememberUpdatedState(onThemeChanged)
    LaunchedEffect(themePreference) {
        latestOnThemeChanged(isDarkTheme)
    }
    val themeState = remember(isDarkTheme, themePreference) {
        MapmoryThemeState(
            isDark = isDarkTheme,
            onThemeChange = { shouldUseDarkTheme ->
                isDarkTheme = shouldUseDarkTheme
                themePreference.saveIsDarkTheme(shouldUseDarkTheme)
                latestOnThemeChanged(shouldUseDarkTheme)
            },
        )
    }
    val navController = rememberNavController()
    val navigator = remember(navController) { MapmoryNavigator(navController) }
    val backHandlerRegistry = remember { MapmoryBackHandlerRegistry() }
    val latestNavigateBack = rememberUpdatedState {
        backHandlerRegistry.handleBack() || navigator.navigateBack()
    }
    var showSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(SplashDurationMillis)
        showSplash = false
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
            if (showSplash) {
                MapmorySplashScreen(contentWindowInsets = contentWindowInsets)
            } else {
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
}

private const val SplashDurationMillis = 900L

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
