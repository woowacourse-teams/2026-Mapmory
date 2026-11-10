package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.data.settings.MapmoryThemePalette
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
import com.mapmory.shared.presentation.triprecord.TripRecordListLayout
import com.mapmory.shared.presentation.triprecord.screen.ProvideTripRecordPalettes
import com.mapmory.shared.presentation.triprecord.screen.TripRecordTheme
import com.mapmory.shared.update.ForceUpdateState
import com.mapmory.shared.update.ForceUpdateGate
import com.mapmory.shared.update.awaitSplashUpdateCheck
import kotlin.time.TimeSource

@Composable
fun MapmoryApp(
    container: AppContainer? = null,
    navigation: MapmoryNavigation? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    onThemeChanged: (Boolean) -> Unit = {},
    analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
    developerToolsInfo: MapmoryDeveloperToolsInfo? = null,
    showTimelineLayoutSwitcher: Boolean = true,
    updateState: ForceUpdateState? = null,
    onRetryUpdateCheck: () -> Unit = {},
) {
    val ownedContainer = remember(container) {
        if (container == null) createInMemoryAppContainer() else null
    }
    val appContainer = requireNotNull(container ?: ownedContainer)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foregroundSince by remember(lifecycle) {
        mutableStateOf(
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) TimeSource.Monotonic.markNow() else null,
        )
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foregroundSince = TimeSource.Monotonic.markNow()
                Lifecycle.Event.ON_STOP -> foregroundSince = null
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val themePreference = appContainer.themePreference
    var isDarkTheme by remember(themePreference) {
        mutableStateOf(themePreference.loadIsDarkTheme())
    }
    var themePalette by remember(themePreference) {
        mutableStateOf(themePreference.loadThemePalette())
    }
    val latestOnThemeChanged by rememberUpdatedState(onThemeChanged)
    LaunchedEffect(themePreference) {
        latestOnThemeChanged(isDarkTheme)
    }
    val themeState = remember(isDarkTheme, themePalette, themePreference) {
        MapmoryThemeState(
            isDark = isDarkTheme,
            palette = themePalette,
            onThemeChange = { shouldUseDarkTheme ->
                isDarkTheme = shouldUseDarkTheme
                themePreference.saveIsDarkTheme(shouldUseDarkTheme)
                latestOnThemeChanged(shouldUseDarkTheme)
            },
            onPaletteChange = { selectedPalette ->
                themePalette = selectedPalette
                themePreference.saveThemePalette(selectedPalette)
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
    val accountState = appContainer.accountSession?.state?.collectAsState()?.value
    val accountGeneration = accountState?.generation ?: 0L
    val navController = key(accountGeneration) { rememberNavController() }
    val navigator = remember(navController) { MapmoryNavigator(navController) }
    val backHandlerRegistry = remember(accountGeneration) { MapmoryBackHandlerRegistry() }
    val latestNavigateBack = rememberUpdatedState {
        updateState?.isBlocking == true ||
            backHandlerRegistry.handleBack(navController.currentBackStackEntry?.id) ||
            navigator.navigateBack()
    }
    var edgeGestureOwnerId by remember { mutableStateOf<String?>(null) }
    var edgeGestureWasInternal by remember { mutableStateOf(false) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var savedJournalLayout by rememberSaveable { mutableStateOf(TripRecordListLayout.PHOTO.name) }
    val journalLayout = when (savedJournalLayout) {
        TripRecordListLayout.TIMELINE.name -> TripRecordListLayout.TIMELINE
        TripRecordListLayout.COLLECTION.name -> TripRecordListLayout.COLLECTION
        else -> TripRecordListLayout.PHOTO
    }
    var isTravelAchievementsEnabled by rememberSaveable { mutableStateOf(false) }
    var isMascotPreviewEnabled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        if (showSplash || updateState?.isChecking == true) {
            showSplash = true
            awaitSplashUpdateCheck(updateState, SplashDurationMillis)
            showSplash = false
        }
    }

    DisposableEffect(navigation, navigator, backHandlerRegistry) {
        navigation?.bindBackHandler { latestNavigateBack.value() }
        onDispose { navigation?.unbindBackHandler() }
    }
    DisposableEffect(ownedContainer) {
        onDispose { ownedContainer?.close() }
    }

    CompositionLocalProvider(
        com.mapmory.shared.presentation.group.LocalGroupInvitationInbox provides navigation?.invitations,
        com.mapmory.shared.presentation.triprecord.screen.LocalGroupFeatureEnabled provides true,
        com.mapmory.shared.navigation.LocalNavigationGestureMotion provides remember { com.mapmory.shared.navigation.NavigationGestureMotion() },
        LocalMapmoryTheme provides themeState,
        LocalMapmoryAnalytics provides analytics,
        LocalTeamDevice provides teamDeviceState,
    ) {
        ProvideTripRecordPalettes(isDark = isDarkTheme, themePalette = themePalette) {
            TripRecordTheme {
                if (showSplash || updateState?.isChecking == true) {
                    MapmorySplashScreen(contentWindowInsets = contentWindowInsets)
                } else {
                    ProvideScrollToTop {
                        MapmoryNavHost(
                            navController = navController,
                            navigator = navigator,
                            navigation = navigation,
                            container = appContainer,
                            foregroundSince = foregroundSince,
                            backHandlerRegistry = backHandlerRegistry,
                            contentWindowInsets = contentWindowInsets,
                            developerToolsInfo = developerToolsInfo,
                            showTimelineLayoutSwitcher = showTimelineLayoutSwitcher,
                            journalLayout = journalLayout,
                            onJournalLayoutChange = { savedJournalLayout = it.name },
                            isTravelAchievementsEnabled = isTravelAchievementsEnabled,
                            onTravelAchievementsEnabledChange = { isTravelAchievementsEnabled = it },
                            isMascotPreviewEnabled = isMascotPreviewEnabled,
                            onMascotPreviewEnabledChange = { isMascotPreviewEnabled = it },
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
                                    backHandlerRegistry.handleGestureForward(edgeGestureOwnerId, restoring = true)
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
                                    backHandlerRegistry.handleGestureBack(edgeGestureOwnerId, restoring = true)
                                } else {
                                    navigator.navigateBackKeepingState()
                                }
                                edgeGestureOwnerId = null
                                edgeGestureWasInternal = false
                            },
                        )
                    }
                }
                var dismissedWithdrawalNotice by remember(accountState?.generation) { mutableStateOf(false) }
                if (accountState?.withdrawalUnconfirmed == true && !dismissedWithdrawalNotice) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { dismissedWithdrawalNotice = true },
                        title = { androidx.compose.material3.Text("탈퇴 결과를 확인하지 못했어요") },
                        text = { androidx.compose.material3.Text("로그인 정보가 만료되어 탈퇴 여부를 확인할 수 없어요. 그룹 탭에서 다시 로그인해 계정을 확인하고, 필요한 경우 탈퇴를 다시 진행해 주세요.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { dismissedWithdrawalNotice = true }) {
                                androidx.compose.material3.Text("확인")
                            }
                        },
                    )
                }
                val withdrawalLauncher = com.mapmory.shared.presentation.auth.LocalSocialLoginLauncher.current
                com.mapmory.shared.presentation.auth.AccountRecoveryDialog(
                    session = appContainer.accountSession,
                    controller = appContainer.accountLoginController,
                    launcher = withdrawalLauncher,
                )
                if (accountState?.pendingLogout == true && !accountState.transitioning) {
                    val logoutUi = appContainer.accountLoginController?.state?.collectAsState()?.value
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {
                            if (accountState.pendingWithdrawal) appContainer.accountLoginController?.leaveWithdrawalRecovery()
                        },
                        title = { androidx.compose.material3.Text(if (accountState.pendingWithdrawal) "회원 탈퇴를 마무리해 주세요" else "로그아웃을 마무리해 주세요") },
                        text = { androidx.compose.material3.Text(logoutUi?.message ?: if (accountState.pendingWithdrawal) "탈퇴가 중단됐어요. 다시 시도하거나 닫을 수 있어요. 닫으면 기기의 로그인 정보를 정리해요. 탈퇴 결과는 다시 로그인해 확인해 주세요." else "로그인 정보와 계정 화면을 정리하고 있어요. 다시 시도해 주세요.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { if (accountState.pendingWithdrawal) appContainer.accountLoginController?.withdraw(withdrawalLauncher) else appContainer.accountLoginController?.logout() },
                                enabled = logoutUi?.busy != true,
                            ) { androidx.compose.material3.Text(if (accountState.pendingWithdrawal) "회원 탈퇴 다시 시도" else "로그아웃 다시 시도") }
                        },
                        dismissButton = {
                            if (accountState.pendingWithdrawal) androidx.compose.material3.TextButton(
                                onClick = { appContainer.accountLoginController?.leaveWithdrawalRecovery() },
                                enabled = logoutUi?.busy != true,
                            ) { androidx.compose.material3.Text("닫기") }
                        },
                    )
                }
                if (accountState?.transitioning == true) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                            androidx.compose.foundation.layout.Column(Modifier.padding(28.dp)) {
                                androidx.compose.material3.CircularProgressIndicator()
                                androidx.compose.material3.Text(if (accountState.pendingWithdrawal) "회원 탈퇴를 처리하고 있어요" else if (accountState.pendingLogout) "로그아웃하고 있어요" else "계정을 확인하고 있어요")
                            }
                        }
                    }
                }
                updateState?.let { ForceUpdateGate(it, onRetryUpdateCheck) }
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
