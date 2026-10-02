package com.mapmory.shared.presentation.photo

internal fun shouldReuseLocationMetadata(
    previousModifiedAtSeconds: Long?,
    currentModifiedAtSeconds: Long,
): Boolean = previousModifiedAtSeconds != null &&
    previousModifiedAtSeconds == currentModifiedAtSeconds
