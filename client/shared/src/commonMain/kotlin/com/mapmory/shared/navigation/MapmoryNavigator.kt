package com.mapmory.shared.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute

internal class MapmoryNavigator(
    private val navController: NavHostController,
) {
    private data class ForwardEntry(val route: Any, val fromEntryId: String)
    private val forwardEntries = mutableListOf<ForwardEntry>()

    fun navigateBackKeepingState(): Boolean {
        val entry = navController.currentBackStackEntry ?: return false
        val previous = navController.previousBackStackEntry ?: return false
        val route: Any = when {
            entry.destination.hasRoute<EditorRoute>() -> entry.toRoute<EditorRoute>()
            entry.destination.hasRoute<DetailRoute>() -> entry.toRoute<DetailRoute>()
            entry.destination.hasRoute<RecordsRoute>() -> entry.toRoute<RecordsRoute>()
            entry.destination.hasRoute<GroupsRoute>() -> GroupsRoute
            entry.destination.hasRoute<ProfileRoute>() -> ProfileRoute
            entry.destination.hasRoute<SettingsRoute>() -> SettingsRoute
            else -> return false
        }
        if (!navController.popBackStack(entry.destination.id, inclusive = true, saveState = true)) return false
        forwardEntries += ForwardEntry(route, previous.id)
        return true
    }

    fun navigateForward(): Boolean {
        val entry = forwardEntries.lastOrNull() ?: return false
        if (navController.currentBackStackEntry?.id != entry.fromEntryId) return false
        forwardEntries.removeAt(forwardEntries.lastIndex)
        navController.navigate(entry.route) { restoreState = true }
        return true
    }

    private fun clearForwardHistory() {
        forwardEntries.forEach { navController.clearBackStack(it.route) }
        forwardEntries.clear()
    }

    fun navigateBack(): Boolean {
        clearForwardHistory()
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

    fun navigateToGroups() {
        navigateToTab(GroupsRoute)
    }

    fun navigateToProfile() {
        navigateToTab(ProfileRoute)
    }

    fun navigateToSettings() {
        navigateToTab(SettingsRoute)
    }

    fun navigateToDeveloperTools() {
        navController.navigate(DeveloperToolsRoute)
    }

    fun navigateToEditor(
        recordId: Long? = null,
        selectedLocationId: Long? = null,
    ) {
        // Ignore subsequent taps from the outgoing map/list, including other locations.
        if (navController.currentDestination?.hasRoute<EditorRoute>() == true) return
        clearForwardHistory()
        navController.navigate(EditorRoute(recordId, selectedLocationId))
    }

    fun navigateToDetail(
        recordId: Long,
        locationName: String? = null,
        latestDate: String? = null,
        photoCount: Int? = null,
    ) {
        // Navigation updates the current entry synchronously, before the old list leaves composition.
        // Ignore any further card clicks delivered from that outgoing list, including other IDs.
        if (navController.currentDestination?.hasRoute<DetailRoute>() == true) return
        clearForwardHistory()
        navController.navigate(DetailRoute(recordId, locationName, latestDate, photoCount))
    }

    fun navigateAfterEdit(recordId: Long) {
        clearForwardHistory()
        if (!navController.popBackStack()) {
            navigateToDetail(recordId)
        }
    }

    private fun navigateToTab(route: Any) {
        clearForwardHistory()
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
