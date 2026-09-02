package com.mapmory.shared.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.logging.mapmoryDebugLog
import com.mapmory.shared.presentation.map.route.MapRoute as MapScreenRoute
import com.mapmory.shared.presentation.triprecord.route.TripProfileRoute
import com.mapmory.shared.presentation.triprecord.route.TripRecordDetailRoute
import com.mapmory.shared.presentation.triprecord.route.TripRecordEditorRoute
import com.mapmory.shared.presentation.triprecord.route.TripRecordListRoute

@Composable
internal fun MapmoryNavHost(
    navController: NavHostController,
    navigator: MapmoryNavigator,
    container: AppContainer,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    contentWindowInsets: WindowInsets,
) {
    val tripRecordRevision by container.tripRecordRevision.collectAsState()
    NavHost(
        navController = navController,
        startDestination = MapRoute,
    ) {
        composable<MapRoute> { backStackEntry ->
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
                backHandlerRegistry = backHandlerRegistry,
                tripRecordRevision = tripRecordRevision,
                onOpenRecords = navigator::navigateToRecords,
                onOpenEditor = { locationId ->
                    navigator.navigateToEditor(selectedLocationId = locationId)
                },
                onOpenProfile = navigator::navigateToProfile,
            )
        }

        composable<RecordsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<RecordsRoute>()
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
                initialLocationId = route.locationId,
                tripRecordRevision = tripRecordRevision,
                onOpenMap = navigator::navigateToMap,
                onOpenEditor = { navigator.navigateToEditor() },
                onOpenDetail = navigator::navigateToDetail,
                onOpenProfile = navigator::navigateToProfile,
            )
        }

        composable<EditorRoute> { backStackEntry ->
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
                    contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                ),
                recordId = route.recordId,
                selectedLocationId = route.selectedLocationId,
                viewModel = viewModel,
                regionCatalog = container.regionCatalog,
                backHandlerRegistry = backHandlerRegistry,
                onBack = { navigator.navigateBack() },
                onSaved = { wasEditing, recordId ->
                    if (wasEditing) {
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

        composable<ProfileRoute> { backStackEntry ->
            LaunchedEffect(backStackEntry) {
                mapmoryDebugLog(NavigationLogTag, "screen=profile")
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
            )
        }

        composable<DetailRoute> { backStackEntry ->
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
                tripRecordRevision = tripRecordRevision,
                viewModel = viewModel,
                onBack = { navigator.navigateBack() },
                onEdit = { recordId ->
                    navigator.navigateToEditor(recordId = recordId)
                },
                onDeleted = { navigator.navigateToRecords() },
                onOpenMap = navigator::navigateToMap,
                onOpenRecords = { navigator.navigateToRecords() },
                onOpenEditor = { navigator.navigateToEditor() },
                onOpenProfile = navigator::navigateToProfile,
            )
        }
    }
}

private const val NavigationLogTag = "MapmoryNavigation"
