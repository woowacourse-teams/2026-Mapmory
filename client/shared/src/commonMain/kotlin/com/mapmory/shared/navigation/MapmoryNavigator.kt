package com.mapmory.shared.navigation

import androidx.navigation.NavHostController

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

    fun navigateToDetail(recordId: Long) {
        navController.navigate(DetailRoute(recordId))
    }

    fun navigateAfterEdit(recordId: Long) {
        if (!navController.popBackStack()) {
            navigateToDetail(recordId)
        }
    }

    private fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo<MapRoute> {
                inclusive = false
            }
            launchSingleTop = true
        }
    }
}
