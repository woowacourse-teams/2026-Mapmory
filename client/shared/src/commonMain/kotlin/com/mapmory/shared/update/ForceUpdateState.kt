package com.mapmory.shared.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Mutated on the UI thread by each platform's Remote Config adapter. */
class ForceUpdateState(val currentVersion: String, val storeUrl: String) {
    var minimumVersion by mutableStateOf<String?>(null)
        private set
    var isChecking by mutableStateOf(true)
        private set
    var isStoreUpdateAvailable by mutableStateOf(false)
        private set

    val isUpdateRequired: Boolean
        get() = requiresUpdate(currentVersion, minimumVersion.orEmpty())
    val isBlocking: Boolean
        get() = isChecking || isUpdateRequired

    /** Invalid remote values must not clear a previously accepted restriction. */
    fun applyMinimumVersion(value: String): Boolean {
        val normalized = value.trim()
        if (parseVersion(normalized) == null) return false
        minimumVersion = normalized
        return true
    }

    fun finishCheck() {
        isChecking = false
    }

    fun reportStoreUpdateAvailability(isAvailable: Boolean) {
        isStoreUpdateAvailable = isAvailable
    }

    fun dismissStoreUpdate() {
        isStoreUpdateAvailable = false
    }
}

internal fun requiresUpdate(installed: String, minimum: String): Boolean {
    // Internal Android builds append -internal to the public version name.
    val current = parseVersion(installed.substringBefore('-')) ?: return false
    val required = parseVersion(minimum) ?: return false
    for (index in 0..2) {
        if (current[index] != required[index]) return current[index] < required[index]
    }
    return false
}

private fun parseVersion(value: String): List<Long>? {
    val parts = value.split('.')
    if (parts.size !in 1..3) return null
    val numbers = parts.map { part ->
        if (part.isEmpty() || part.any { it !in '0'..'9' }) return null
        part.toLongOrNull() ?: return null
    }
    return numbers + List(3 - numbers.size) { 0L }
}
