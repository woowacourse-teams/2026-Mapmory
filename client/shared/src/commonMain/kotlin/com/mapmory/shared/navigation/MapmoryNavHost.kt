package com.mapmory.shared.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mapmory.shared.app.BackgroundTripRecordCompletion
import com.mapmory.shared.presentation.triprecord.screen.TripRecordSavedCard
import kotlinx.coroutines.delay
import kotlin.time.TimeSource
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.logging.mapmoryDebugLog
import com.mapmory.shared.presentation.developer.DeveloperToolsScreen
import com.mapmory.shared.presentation.map.route.MapRoute as MapScreenRoute
import com.mapmory.shared.presentation.triprecord.route.TripProfileRoute
import com.mapmory.shared.presentation.triprecord.route.TripAchievementsScreenRoute
import com.mapmory.shared.presentation.triprecord.route.TripRecordDetailRoute
import com.mapmory.shared.presentation.triprecord.route.TripRecordEditorRoute
import com.mapmory.shared.presentation.triprecord.route.TripRecordListRoute
import com.mapmory.shared.presentation.triprecord.route.TripSettingsRoute
import com.mapmory.shared.presentation.triprecord.screen.TripAchievementBadge
import com.mapmory.shared.presentation.triprecord.screen.TripAchievementUnlockDialog
import com.mapmory.shared.presentation.triprecord.screen.TripTimelineVideoJobMonitor
import com.mapmory.shared.presentation.triprecord.screen.newlyEarnedTripAchievementBadges
import com.mapmory.shared.presentation.triprecord.TripRecordListLayout

@Composable
internal fun MapmoryNavHost(
    navController: NavHostController,
    navigator: MapmoryNavigator,
    container: AppContainer,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    contentWindowInsets: WindowInsets,
    developerToolsInfo: MapmoryDeveloperToolsInfo?,
    showTimelineLayoutSwitcher: Boolean,
    journalLayout: TripRecordListLayout,
    onJournalLayoutChange: (TripRecordListLayout) -> Unit,
    isTravelAchievementsEnabled: Boolean,
    onTravelAchievementsEnabledChange: (Boolean) -> Unit,
    isMascotPreviewEnabled: Boolean,
    onMascotPreviewEnabledChange: (Boolean) -> Unit,
) {
    val tripRecordRevision by container.tripRecordRevision.collectAsState()
    val backgroundSaves by container.backgroundTripRecordSaver.saves.collectAsState()
    var newlyEarnedBadges by remember { mutableStateOf<List<TripAchievementBadge>?>(null) }
    val gestureMotion = LocalNavigationGestureMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val completedCards = remember { mutableStateListOf<BackgroundTripRecordCompletion>() }
    var foregroundSince by remember(lifecycle) {
        mutableStateOf(if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) TimeSource.Monotonic.markNow() else null)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foregroundSince = TimeSource.Monotonic.markNow()
                Lifecycle.Event.ON_STOP -> {
                    foregroundSince = null
                    completedCards.clear()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    val editing = currentEntry?.destination?.hasRoute<EditorRoute>() == true
    val accessibilityManager = LocalAccessibilityManager.current
    val currentCard = completedCards.firstOrNull()
    LaunchedEffect(currentCard?.recordId, editing, accessibilityManager) {
        if (currentCard == null) return@LaunchedEffect
        val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = 6_000L,
            containsIcons = true,
            containsText = true,
            containsControls = !editing,
        ) ?: 6_000L
        delay(timeout)
        completedCards.removeAll { it.recordId == currentCard.recordId }
    }
    LaunchedEffect(container.backgroundTripRecordSaver, developerToolsInfo, isTravelAchievementsEnabled) {
        container.backgroundTripRecordSaver.completedSaves.collect { completion ->
            // Ignore completions delivered while away, including those queued before this host mounted.
            if (shouldShowSavedRecordCard(
                    completion.completedAt, foregroundSince,
                    lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                )) {
                completedCards.add(completion)
            }
            if (developerToolsInfo == null || !isTravelAchievementsEnabled) return@collect
            val previousStatistics = completion.previousStatistics ?: return@collect
            val currentStatistics = try {
                container.tripStatisticsRepository.getStatistics().getOrNull()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: return@collect
            val unlocked = newlyEarnedTripAchievementBadges(previousStatistics, currentStatistics)
            if (unlocked.isNotEmpty()) {
                newlyEarnedBadges = (newlyEarnedBadges.orEmpty() + unlocked).distinctBy { it.id }
            }
        }
    }
    Box(Modifier.fillMaxSize().then(gestureMotion.renderObserver { navController.currentBackStackEntry?.id })) {
        container.shareVideoRepository?.let { repository ->
            TripTimelineVideoJobMonitor(repository, container.shareVideoJobStore)
        }
        NavHost(
            navController = navController,
            startDestination = if ((container.accountSession?.state?.value?.generation ?: 0) > 0 &&
                container.accountSession?.state?.value?.stage != "logged_out") GroupsRoute else MapRoute,
            // Bottom-tab navigation is a state switch, so it should not use push-style motion on iOS.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
        composable<MapRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            LaunchedEffect(backStackEntry) {
                mapmoryDebugLog(NavigationLogTag, "screen=map")
            }
            val viewModel = viewModel {
                container.viewModelFactory.createMapViewModel()
            }
            MapScreenRoute(
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                viewModel = viewModel,
                regionCatalog = container.regionCatalog,
                onboardingPreference = container.onboardingPreference,
                backHandlerRegistry = backHandlerRegistry,
                backHandlerOwnerId = backStackEntry.id,
                tripRecordRevision = tripRecordRevision,
                onOpenRecords = navigator::navigateToRecords,
                onOpenEditor = { locationId ->
                    navigator.navigateToEditor(selectedLocationId = locationId)
                },
                onOpenProfile = navigator::navigateToProfile,
                onOpenGroups = navigator::navigateToGroups,
                onOpenSettings = navigator::navigateToSettings,
            )
        }

        composable<RecordsRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            val route = backStackEntry.toRoute<RecordsRoute>()
            val requestedLocationId by backStackEntry.savedStateHandle
                .getStateFlow<Long?>(RecordsLocationKey, route.locationId)
                .collectAsState()
            LaunchedEffect(backStackEntry) {
                mapmoryDebugLog(
                    NavigationLogTag,
                    "screen=records, locationId=${route.locationId}",
                )
            }
            val viewModel = viewModel {
                container.viewModelFactory.createTripRecordListViewModel()
            }
            TripRecordListRoute(
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                viewModel = viewModel,
                initialLocationId = requestedLocationId,
                tripRecordRevision = tripRecordRevision,
                pendingSaves = backgroundSaves,
                onRetryPendingSave = container.backgroundTripRecordSaver::retry,
                onDismissPendingSave = container.backgroundTripRecordSaver::dismissFailure,
                onOpenMap = navigator::navigateToMap,
                onOpenEditor = {
                    navigator.navigateToEditor(selectedLocationId = requestedLocationId)
                },
                onOpenDetail = navigator::navigateToDetail,
                onOpenProfile = navigator::navigateToProfile,
                onOpenGroups = navigator::navigateToGroups,
                onOpenSettings = navigator::navigateToSettings,
                shareVideoRepository = container.shareVideoRepository,
                shareVideoJobStore = container.shareVideoJobStore,
                onboardingPreference = container.onboardingPreference,
                tripStatisticsRepository = container.tripStatisticsRepository,
                backHandlerRegistry = backHandlerRegistry,
                backHandlerOwnerId = backStackEntry.id,
                layout = journalLayout,
                showLayoutSwitcher = showTimelineLayoutSwitcher,
                onLayoutChange = onJournalLayoutChange,
            )
        }

        composable<EditorRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            val route = backStackEntry.toRoute<EditorRoute>()
            LaunchedEffect(backStackEntry) {
                mapmoryDebugLog(
                    NavigationLogTag,
                    "screen=editor, recordId=${route.recordId}, " +
                        "selectedLocationId=${route.selectedLocationId}",
                )
            }
            val viewModel = viewModel {
                container.viewModelFactory.createTripRecordEditorViewModel()
            }
            TripRecordEditorRoute(
                modifier = Modifier.windowInsetsPadding(
                    contentWindowInsets.only(WindowInsetsSides.Horizontal),
                ),
                recordId = route.recordId,
                selectedLocationId = route.selectedLocationId,
                viewModel = viewModel,
                regionCatalog = container.regionCatalog,
                tripStatisticsRepository = container.tripStatisticsRepository,
                showTravelAchievements = developerToolsInfo != null && isTravelAchievementsEnabled,
                backHandlerRegistry = backHandlerRegistry,
                backHandlerOwnerId = backStackEntry.id,
                onBack = { navigator.navigateBack() },
                saveNewRecordInBackground = route.albumGroupId == null,
                onSaved = { wasEditing, recordId ->
                    if (route.albumGroupId != null) {
                        container.albumShareCoordinator?.start(route.albumGroupId, recordId, route.albumId)
                        navigator.navigateBack()
                    } else if (wasEditing) {
                        navigator.navigateAfterEdit(recordId)
                    } else {
                        navigator.navigateToRecords()
                    }
                },
                onOpenMap = navigator::navigateToMap,
                onOpenRecords = { navigator.navigateToRecords() },
                onOpenProfile = navigator::navigateToProfile,
            )
        }

        composable<GroupsRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            com.mapmory.shared.presentation.group.GroupAccountRoute(
                container = container,
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                onMapClick = navigator::navigateToMap,
                onRecordClick = { navigator.navigateToRecords() },
                onProfileClick = navigator::navigateToProfile,
                onSettingsClick = navigator::navigateToSettings,
                initialGroupId = androidx.compose.runtime.remember(backStackEntry) { backStackEntry.savedStateHandle.remove<Long>("albumGroupId") },
                initialAlbumId = androidx.compose.runtime.remember(backStackEntry) { backStackEntry.savedStateHandle.remove<Long>("albumId") },
                onCreateRecord = { groupId, albumId ->
                    backStackEntry.savedStateHandle["albumGroupId"] = groupId
                    backStackEntry.savedStateHandle["albumId"] = albumId
                    navigator.navigateToEditor(albumGroupId = groupId, albumId = albumId)
                },
                backHandlerRegistry = backHandlerRegistry,
                backHandlerOwnerId = backStackEntry.id,
            )
        }

        composable<GroupPreviewRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            com.mapmory.shared.presentation.group.GroupAccountRoute(
                container = container,
                onMapClick = navigator::navigateToMap,
                onRecordClick = { navigator.navigateToRecords() },
                onProfileClick = navigator::navigateToProfile,
                onSettingsClick = navigator::navigateToSettings,
                initialGroupId = androidx.compose.runtime.remember(backStackEntry) { backStackEntry.savedStateHandle.remove<Long>("albumGroupId") },
                initialAlbumId = androidx.compose.runtime.remember(backStackEntry) { backStackEntry.savedStateHandle.remove<Long>("albumId") },
                onCreateRecord = { groupId, albumId ->
                    backStackEntry.savedStateHandle["albumGroupId"] = groupId
                    backStackEntry.savedStateHandle["albumId"] = albumId
                    navigator.navigateToEditor(albumGroupId = groupId, albumId = albumId)
                },
                backHandlerRegistry = backHandlerRegistry,
                backHandlerOwnerId = backStackEntry.id,
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
            )
        }

        composable<ProfileRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            LaunchedEffect(backStackEntry) {
                mapmoryDebugLog(NavigationLogTag, "screen=statistics")
            }
            val viewModel = viewModel {
                container.viewModelFactory.createTripStatisticsViewModel()
            }
            TripProfileRoute(
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                viewModel = viewModel,
                tripRecordRevision = tripRecordRevision,
                onOpenMap = navigator::navigateToMap,
                onOpenRecords = navigator::navigateToRecords,
                onOpenEditor = { navigator.navigateToEditor() },
                onOpenProfile = navigator::navigateToProfile,
                onOpenGroups = navigator::navigateToGroups,
                onOpenSettings = navigator::navigateToSettings,
                onOpenAchievements = navigator::navigateToAchievements,
                showTravelAchievements = developerToolsInfo != null && isTravelAchievementsEnabled,
            )
        }

        composable<TripAchievementsRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            if (developerToolsInfo != null && isTravelAchievementsEnabled) {
                val viewModel = viewModel {
                    container.viewModelFactory.createTripStatisticsViewModel()
                }
                TripAchievementsScreenRoute(
                    modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                    viewModel = viewModel,
                    tripRecordRevision = tripRecordRevision,
                    onBack = { navigator.navigateBack() },
                )
            } else {
                LaunchedEffect(Unit) { navigator.navigateBack() }
            }
        }

        composable<SettingsRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            LaunchedEffect(backStackEntry) {
                mapmoryDebugLog(NavigationLogTag, "screen=settings")
            }
            TripSettingsRoute(
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                onOpenMap = navigator::navigateToMap,
                onOpenRecords = navigator::navigateToRecords,
                onOpenProfile = navigator::navigateToProfile,
                onOpenGroups = navigator::navigateToGroups,
                onOpenSettings = navigator::navigateToSettings,
                developerToolsInfo = developerToolsInfo,
                onOpenDeveloperTools = navigator::navigateToDeveloperTools,
                serviceFeedbackRepository = container.serviceFeedbackRepository,
                accountSession = container.accountSession,
                accountController = container.accountLoginController,
            )
        }

        composable<DeveloperToolsRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            if (developerToolsInfo != null) {
                DeveloperToolsScreen(
                    info = developerToolsInfo,
                    isTravelAchievementsEnabled = isTravelAchievementsEnabled,
                    onTravelAchievementsEnabledChange = onTravelAchievementsEnabledChange,
                    isMascotPreviewEnabled = isMascotPreviewEnabled,
                    onMascotPreviewEnabledChange = onMascotPreviewEnabledChange,
                    onBack = { navigator.navigateBack() },
                    modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                )
            } else {
                LaunchedEffect(Unit) { navigator.navigateBack() }
            }
        }

        composable<DetailRoute> { backStackEntry ->
            SideEffect { gestureMotion.composedEntryId = backStackEntry.id }
            val route = backStackEntry.toRoute<DetailRoute>()
            LaunchedEffect(backStackEntry) {
                mapmoryDebugLog(
                    NavigationLogTag,
                    "screen=detail, recordId=${route.recordId}",
                )
            }
            val viewModel = viewModel {
                container.viewModelFactory.createTripRecordDetailViewModel()
            }
            TripRecordDetailRoute(
                modifier = Modifier.windowInsetsPadding(
                    contentWindowInsets.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
                recordId = route.recordId,
                initialLocationName = route.locationName,
                initialLatestDate = route.latestDate,
                initialPhotoCount = route.photoCount,
                tripRecordRevision = tripRecordRevision,
                viewModel = viewModel,
                backHandlerRegistry = backHandlerRegistry,
                backHandlerOwnerId = backStackEntry.id,
                onBack = { navigator.navigateBack() },
                onEdit = { recordId ->
                    navigator.navigateToEditor(recordId = recordId)
                },
                onDeleted = { navigator.navigateToRecords() },
                onOpenMap = navigator::navigateToMap,
                onOpenRecords = { navigator.navigateToRecords() },
                onOpenEditor = { navigator.navigateToEditor() },
                onOpenProfile = navigator::navigateToProfile,
                isTimelineLayout = journalLayout == TripRecordListLayout.TIMELINE,
            )
        }
        }

        currentCard?.let { completion ->
            val hasBottomBar = currentEntry?.destination?.let { destination ->
                destination.hasRoute<MapRoute>() || destination.hasRoute<RecordsRoute>() ||
                    destination.hasRoute<ProfileRoute>() || destination.hasRoute<SettingsRoute>() ||
                    destination.hasRoute<GroupsRoute>()
            } == true
            TripRecordSavedCard(
                completion = completion,
                // Do not leave another unfinished editor or discard its local selection state.
                onView = if (editing) null else {
                    {
                        completedCards.removeAll { it.recordId == completion.recordId }
                        navigator.navigateToSavedRecord(completion.recordId, completion.locationName, completion.photoCount)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (hasBottomBar) 76.dp else 16.dp),
            )
        }

        newlyEarnedBadges?.let { badges ->
            if (badges.isNotEmpty()) {
                TripAchievementUnlockDialog(badges) { newlyEarnedBadges = null }
            }
        }

    }
}

private const val NavigationLogTag = "MapmoryNavigation"

/** A completion from a previous foreground session must not reappear after returning to the app. */
internal fun shouldShowSavedRecordCard(
    completedAt: TimeSource.Monotonic.ValueTimeMark,
    foregroundSince: TimeSource.Monotonic.ValueTimeMark?,
    isStarted: Boolean,
): Boolean = isStarted && foregroundSince != null && completedAt >= foregroundSince
