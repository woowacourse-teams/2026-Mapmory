package com.mapmory.shared.presentation.photo

internal fun shouldReuseCoordinates(
    previousModifiedAtSeconds: Long?,
    previousLatitude: Double?,
    previousLongitude: Double?,
    currentModifiedAtSeconds: Long,
): Boolean = previousModifiedAtSeconds == currentModifiedAtSeconds &&
    previousLatitude != null &&
    previousLongitude != null
