package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.presentation.theme.MapmoryTypography

@Composable
internal fun TripRecordTheme(content: @Composable () -> Unit) {
    val palette = TripRecordPalette.current
    val colors = if (LocalMapmoryTheme.current.isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primarySoft,
            onPrimaryContainer = palette.headingText,
            inversePrimary = palette.secondaryAccent,
            secondary = palette.secondaryAccent,
            onSecondary = palette.onPrimary,
            secondaryContainer = palette.softSurface,
            onSecondaryContainer = palette.text,
            tertiary = palette.accent,
            onTertiary = palette.onPrimary,
            tertiaryContainer = palette.accentSoft,
            onTertiaryContainer = palette.text,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.bodyText,
            surfaceBright = palette.surfaceElevated,
            surfaceDim = palette.background,
            surfaceContainer = palette.softSurface,
            surfaceContainerHigh = palette.surfaceElevated,
            surfaceContainerHighest = palette.metadataDateBackground,
            surfaceContainerLow = palette.surface,
            surfaceContainerLowest = palette.background,
            inverseSurface = palette.text,
            inverseOnSurface = palette.background,
            error = palette.danger,
            onError = palette.onPrimary,
            errorContainer = palette.photoRecommendBackground,
            onErrorContainer = palette.photoRecommendText,
            outline = palette.line,
            outlineVariant = palette.border,
            scrim = Color.Black.copy(alpha = 0.32f),
            surfaceTint = palette.primary,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primarySoft,
            onPrimaryContainer = palette.headingText,
            inversePrimary = palette.secondaryAccent,
            secondary = palette.secondaryAccent,
            onSecondary = palette.onPrimary,
            secondaryContainer = palette.softSurface,
            onSecondaryContainer = palette.text,
            tertiary = palette.accent,
            onTertiary = palette.onPrimary,
            tertiaryContainer = palette.accentSoft,
            onTertiaryContainer = palette.text,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.surface,
            onSurface = palette.text,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.bodyText,
            surfaceBright = palette.surface,
            surfaceDim = palette.border,
            surfaceContainer = palette.surfaceElevated,
            surfaceContainerHigh = palette.softSurface,
            surfaceContainerHighest = palette.border,
            surfaceContainerLow = palette.background,
            surfaceContainerLowest = palette.surface,
            inverseSurface = palette.text,
            inverseOnSurface = palette.background,
            error = palette.danger,
            onError = Color.White,
            errorContainer = palette.photoRecommendBackground,
            onErrorContainer = palette.photoRecommendText,
            outline = palette.line,
            outlineVariant = palette.border,
            scrim = Color.Black.copy(alpha = 0.32f),
            surfaceTint = palette.primary,
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = MapmoryTypography(),
        shapes = MapmoryShapes,
        content = content,
    )
}

private val MapmoryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

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
