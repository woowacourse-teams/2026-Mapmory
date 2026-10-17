package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
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
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsUserProperty
import com.mapmory.shared.analytics.NoOpMapmoryAnalytics
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.navigation.MapmoryNavHost
import com.mapmory.shared.navigation.MapmoryNavigator
import com.mapmory.shared.navigation.PlatformNavigationGestures
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.presentation.splash.MapmorySplashScreen
import com.mapmory.shared.presentation.components.ProvideScrollToTop
import com.mapmory.shared.presentation.triprecord.screen.ProvideTripRecordPalettes
import com.mapmory.shared.presentation.theme.MapmoryTypography
import com.mapmory.shared.update.ForceUpdateState
import com.mapmory.shared.update.ForceUpdateGate
import kotlinx.coroutines.delay

@Composable
fun MapmoryApp(
    container: AppContainer? = null,
    navigation: MapmoryNavigation? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    onThemeChanged: (Boolean) -> Unit = {},
    analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
    developerToolsInfo: MapmoryDeveloperToolsInfo? = null,
    updateState: ForceUpdateState? = null,
    onRetryUpdateCheck: () -> Unit = {},
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
    val teamDevicePreference = appContainer.teamDevicePreference
    var isTeamDevice by remember(teamDevicePreference) {
        mutableStateOf(teamDevicePreference.loadIsTeamDevice())
    }
    LaunchedEffect(analytics, isTeamDevice) {
        analytics.setUserProperty(MapmoryAnalyticsUserProperty.IS_TEAM, isTeamDevice.toString())
    }
    val teamDeviceState = remember(isTeamDevice, teamDevicePreference, updateState) {
        TeamDeviceState(
            appVersion = updateState?.currentVersion.orEmpty(),
            isTeamDevice = isTeamDevice,
            onTeamDeviceChange = { enabled ->
                isTeamDevice = enabled
                teamDevicePreference.saveIsTeamDevice(enabled)
            },
        )
    }
    val navController = rememberNavController()
    val navigator = remember(navController) { MapmoryNavigator(navController) }
    val backHandlerRegistry = remember { MapmoryBackHandlerRegistry() }
    val latestNavigateBack = rememberUpdatedState {
        updateState?.isBlocking == true ||
            backHandlerRegistry.handleBack(navController.currentBackStackEntry?.id) ||
            navigator.navigateBack()
    }
    var edgeGestureOwnerId by remember { mutableStateOf<String?>(null) }
    var edgeGestureWasInternal by remember { mutableStateOf(false) }
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
        com.mapmory.shared.navigation.LocalNavigationGestureMotion provides remember { com.mapmory.shared.navigation.NavigationGestureMotion() },
        LocalMapmoryTheme provides themeState,
        LocalMapmoryAnalytics provides analytics,
        LocalTeamDevice provides teamDeviceState,
    ) {
        ProvideTripRecordPalettes(isDark = isDarkTheme) {
            MaterialTheme(typography = MapmoryTypography()) {
                updateState?.let { ForceUpdateGate(it, onRetryUpdateCheck) }
                if (showSplash) {
                    MapmorySplashScreen(contentWindowInsets = contentWindowInsets)
                } else {
                    ProvideScrollToTop {
                        MapmoryNavHost(
                            navController = navController,
                            navigator = navigator,
                            container = appContainer,
                            backHandlerRegistry = backHandlerRegistry,
                            contentWindowInsets = contentWindowInsets,
                            developerToolsInfo = developerToolsInfo,
                        )
                        if (updateState?.isBlocking != true) PlatformNavigationGestures(
                            onBackStarted = {
                                val ownerId = navController.currentBackStackEntry?.id
                                edgeGestureOwnerId = ownerId
                                edgeGestureWasInternal =
                                    backHandlerRegistry.handleGestureBack(ownerId)
                                edgeGestureWasInternal || navigator.navigateBackKeepingState()
                            },
                            onBackCancelled = {
                                if (edgeGestureWasInternal) {
                                    backHandlerRegistry.handleGestureForward(edgeGestureOwnerId)
                                } else {
                                    navigator.navigateForward()
                                }
                                edgeGestureOwnerId = null
                                edgeGestureWasInternal = false
                            },
                            onForwardStarted = {
                                val ownerId = navController.currentBackStackEntry?.id
                                edgeGestureOwnerId = ownerId
                                edgeGestureWasInternal =
                                    backHandlerRegistry.handleGestureForward(ownerId)
                                edgeGestureWasInternal || navigator.navigateForward()
                            },
                            onForwardCancelled = {
                                if (edgeGestureWasInternal) {
                                    backHandlerRegistry.handleGestureBack(edgeGestureOwnerId)
                                } else {
                                    navigator.navigateBackKeepingState()
                                }
                                edgeGestureOwnerId = null
                                edgeGestureWasInternal = false
                            },
                        )
                    }
                }
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
