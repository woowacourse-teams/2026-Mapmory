package com.mapmory.shared.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute

internal class MapmoryNavigator(
    private val navController: NavHostController,
) {
    fun navigateBack(): Boolean {
        if (navController.currentDestination?.id == navController.graph.startDestinationId) {
            return false
        }
        if (navController.popBackStack()) return true

        navigateToMap()
        return true
    }

    fun navigateToMap() {
        navigateToTab(MapRoute)
    }

    fun navigateToRecords(locationId: Long? = null) {
        navigateToTab(RecordsRoute(locationId))
        // Restoring an entry also restores its old route arguments. Honor this navigation request.
        navController.currentBackStackEntry?.savedStateHandle?.set(RecordsLocationKey, locationId)
    }

    fun navigateToProfile() {
        navigateToTab(ProfileRoute)
    }

    fun navigateToEditor(
        recordId: Long? = null,
        selectedLocationId: Long? = null,
    ) {
        navController.navigate(EditorRoute(recordId, selectedLocationId))
    }

    fun navigateToDetail(recordId: Long, locationName: String? = null) {
        navController.navigate(DetailRoute(recordId, locationName))
    }

    fun navigateAfterEdit(recordId: Long) {
        if (!navController.popBackStack()) {
            navigateToDetail(recordId)
        }
    }

    private fun navigateToTab(route: Any) {
        // Detail/editor entries are temporary flows, not a tab's saved destination.
        while (navController.currentDestination?.let {
                it.hasRoute<DetailRoute>() || it.hasRoute<EditorRoute>()
            } == true
        ) {
            if (!navController.popBackStack()) break
        }
        navController.navigate(route) {
            popUpTo<MapRoute> {
                inclusive = false
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

internal const val RecordsLocationKey = "recordsLocationId"
