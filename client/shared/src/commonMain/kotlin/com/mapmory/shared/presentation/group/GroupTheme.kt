package com.mapmory.shared.presentation.group

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette

/** Bridges Material controls in the group flow to the app's existing light/dark palette. */
@Composable
internal fun GroupTheme(content: @Composable () -> Unit) {
    val palette = TripRecordPalette.current
    val colors = MaterialTheme.colorScheme.copy(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primarySoft,
        onPrimaryContainer = palette.headingText,
        secondary = palette.secondaryAccent,
        onSecondary = palette.onPrimary,
        secondaryContainer = palette.softSurface,
        onSecondaryContainer = palette.headingText,
        background = palette.pageBackground,
        onBackground = palette.headingText,
        surface = palette.surface,
        onSurface = palette.headingText,
        surfaceVariant = palette.softSurface,
        onSurfaceVariant = palette.bodyText,
        surfaceTint = palette.surface,
        surfaceContainerLowest = palette.pageBackground,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surface,
        surfaceContainerHighest = palette.softSurface,
        outline = palette.border,
        outlineVariant = palette.border,
        error = palette.danger,
        onError = palette.surface,
    )
    MaterialTheme(colorScheme = colors, shapes = MaterialTheme.shapes.copy(
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(16.dp),
    )) {
        CompositionLocalProvider(LocalContentColor provides palette.headingText, content = content)
    }
}
