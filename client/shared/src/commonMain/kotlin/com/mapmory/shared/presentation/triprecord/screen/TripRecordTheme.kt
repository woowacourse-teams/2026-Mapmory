package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mapmory.shared.LocalMapmoryTheme

@Composable
internal fun TripRecordTheme(content: @Composable () -> Unit) {
    val palette = TripRecordPalette.current
    val colors = if (LocalMapmoryTheme.current.isDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.background,
            secondary = palette.muted,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.muted,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            error = palette.danger,
        )
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}

@Composable
internal fun TripRecordBackground(
    modifier: Modifier = Modifier,
    backgroundColor: Color = TripRecordPalette.current.background,
    content: @Composable () -> Unit,
) {
    TripRecordTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor,
        ) {
            Box(
                modifier = modifier.fillMaxSize(),
            ) {
                content()
            }
        }
    }
}
