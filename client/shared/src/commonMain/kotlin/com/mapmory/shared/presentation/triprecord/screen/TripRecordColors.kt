package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
internal data class TripRecordColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val photoRecommendText: Color,
    val photoRecommendBackground: Color,
    val photoRecommendBorder: Color,
    val photoGalleryBackground: Color,
    val photoGalleryBorder: Color,
    val pageBackground: Color,
    val softSurface: Color,
    val border: Color,
    val primary: Color,
    val primarySoft: Color,
    val secondaryAccent: Color,
    val onPrimary: Color,
    val headingText: Color,
    val bodyText: Color,
    val secondaryText: Color,
    val navigationDivider: Color,
    val navigationUnselected: Color,
    val navigationSelectedLabel: Color,
    val contentOnMedia: Color,
    val mediaScrim: Color,
    val metadataDateBackground: Color,
)

@Immutable
internal data class TripMapColors(
    val logoText: Color,
    val scopeBackground: Color,
    val scopeBorder: Color,
    val scopeSelectedBackground: Color,
    val scopeSelectedText: Color,
    val scopeUnselectedText: Color,
    val tagBackground: Color,
    val tagText: Color,
    val tagSelectedText: Color,
    val dashboardBadgeText: Color,
    val globeBackground: Color,
    val globeOuterGlow: Color,
    val globeGradientCenter: Color,
    val globeGradientMiddle: Color,
    val globeGradientEdge: Color,
    val globeVisitedFill: Color,
    val globeUnvisitedFill: Color,
    val globeVisitedOutline: Color,
    val globeUnvisitedOutline: Color,
    val globeHighlightCenter: Color,
    val globeHighlightMiddle: Color,
)

@Immutable
internal data class TripStatisticsColors(
    val divider: Color,
    val mapBorder: Color,
    val mapLand: Color,
    val mapOutline: Color,
)

private val LightTripRecordColors = TripRecordColors(
    background = Color(0xFFFAFCFB), surface = Color.White, surfaceElevated = Color(0xFFF7FAF8),
    line = Color(0xFFE1E7E3), text = Color(0xFF1F2924), muted = Color(0xFF89948E),
    accent = Color(0xFF4D9272), accentSoft = Color(0xFFE9F2ED), danger = Color(0xFFC94C57),
    photoRecommendText = Color(0xFFBB4D56), photoRecommendBackground = Color(0xFFFFF1F1),
    photoRecommendBorder = Color(0xFFDB6A70), photoGalleryBackground = Color(0xFFF3F7F4),
    photoGalleryBorder = Color(0xFFD9E6DE), pageBackground = Color(0xFFFAFCFB),
    softSurface = Color(0xFFF0F4F1), border = Color(0xFFE4E9E6), primary = Color(0xFF4D9272),
    primarySoft = Color(0xFFE9F2ED), secondaryAccent = Color(0xFF4A896B), onPrimary = Color.White,
    headingText = Color(0xFF1F2924), bodyText = Color(0xFF5F6E66), secondaryText = Color(0xFF89948E),
    navigationDivider = Color(0xFFE1E7E3), navigationUnselected = Color(0xFF9AA59F),
    navigationSelectedLabel = Color(0xFF5F6E66), contentOnMedia = Color.White,
    mediaScrim = Color.Black.copy(alpha = 0.62f), metadataDateBackground = Color(0xFFF0F4F1),
)

private val DarkTripRecordColors = TripRecordColors(
    background = Color(0xFF111518), surface = Color(0xFF1A1E22), surfaceElevated = Color(0xFF102A32),
    line = Color(0xFF1B363E), text = Color(0xFFE9F4F2), muted = Color(0xFF81999E),
    accent = Color(0xFF35C988), accentSoft = Color(0xFF123E3A), danger = Color(0xFFFF6264),
    photoRecommendText = Color.White, photoRecommendBackground = Color(0xFF382125),
    photoRecommendBorder = Color(0xFF99555D), photoGalleryBackground = Color(0xFF1B2D26),
    photoGalleryBorder = Color(0xFF3E7960), pageBackground = Color(0xFF121518),
    softSurface = Color(0xFF1C2124), border = Color(0xFF2B3135), primary = Color(0xFF35C987),
    primarySoft = Color(0xFF173B2D), secondaryAccent = Color(0xFF67D9A2), onPrimary = Color(0xFF071B12),
    headingText = Color(0xFFF1F5F3), bodyText = Color(0xFFBDC6C2), secondaryText = Color(0xFF89938F),
    navigationDivider = Color(0xFF2C3431), navigationUnselected = Color(0xFF77827D),
    navigationSelectedLabel = Color(0xFFA2ADA7), contentOnMedia = Color.White,
    mediaScrim = Color.Black.copy(alpha = 0.62f), metadataDateBackground = Color(0xFF24292D),
)

private val LightTripMapColors = TripMapColors(
    logoText = Color(0xFF1F2924), scopeBackground = Color(0xFFF1F5F2), scopeBorder = Color(0xFFDCE7E0),
    scopeSelectedBackground = Color.White, scopeSelectedText = Color(0xFF2D4539),
    scopeUnselectedText = Color(0xFF7A8880), tagBackground = Color(0xFFF7FAF8),
    tagText = Color(0xFF6B786F), tagSelectedText = Color.White, dashboardBadgeText = Color(0xFF4A896B),
    globeBackground = Color(0xFFFAFCFB), globeOuterGlow = Color(0xFF789587).copy(alpha = 0.08f),
    globeGradientCenter = Color(0xFFF8FAF8), globeGradientMiddle = Color(0xFFF0F4F1),
    globeGradientEdge = Color(0xFFE7EDE9), globeVisitedFill = Color(0xFF4D9272),
    globeUnvisitedFill = Color(0xFFDCE4DF),
    globeVisitedOutline = Color(0xFF2F7659).copy(alpha = 0.72f),
    globeUnvisitedOutline = Color(0xFFB8C6BD).copy(alpha = 0.72f),
    globeHighlightCenter = Color.White.copy(alpha = 0.34f),
    globeHighlightMiddle = Color.White.copy(alpha = 0.12f),
)

private val DarkTripMapColors = TripMapColors(
    logoText = Color(0xFFF4F8F5), scopeBackground = Color(0xFF151C19), scopeBorder = Color(0xFF2D3A34),
    scopeSelectedBackground = Color(0xFF2A3832), scopeSelectedText = Color(0xFFEEF7F1),
    scopeUnselectedText = Color(0xFF92A09A), tagBackground = Color(0xFF1A2421),
    tagText = Color(0xFFBDC8C2), tagSelectedText = Color(0xFF072118), dashboardBadgeText = Color(0xFF9CE6BF),
    globeBackground = Color(0xFF121518), globeOuterGlow = Color(0xFF7F9ABA).copy(alpha = 0.055f),
    globeGradientCenter = Color(0xFF2A3747), globeGradientMiddle = Color(0xFF1B2533),
    globeGradientEdge = Color(0xFF111923), globeVisitedFill = Color(0xFF35C987),
    globeUnvisitedFill = Color(0xFF2B3546),
    globeVisitedOutline = Color(0xFF8AEBC1).copy(alpha = 0.82f),
    globeUnvisitedOutline = Color(0xFF7C8FAA).copy(alpha = 0.54f),
    globeHighlightCenter = Color.White.copy(alpha = 0.09f),
    globeHighlightMiddle = Color.White.copy(alpha = 0.025f),
)

private val LightTripStatisticsColors = TripStatisticsColors(
    divider = Color(0xFFE6EBE8), mapBorder = Color(0xFFE1E7E3),
    mapLand = Color(0xFFDCE4DF), mapOutline = Color(0xFFC8D3CC),
)

private val DarkTripStatisticsColors = TripStatisticsColors(
    divider = Color(0xFF30363A), mapBorder = Color(0xFF343B40),
    mapLand = Color(0xFF293039), mapOutline = Color(0xFF424B53),
)

internal val TripRecordPalette = staticCompositionLocalOf { LightTripRecordColors }
internal val TripMapPalette = staticCompositionLocalOf { LightTripMapColors }
internal val TripStatisticsPalette = staticCompositionLocalOf { LightTripStatisticsColors }

@Composable
internal fun ProvideTripRecordPalettes(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        TripRecordPalette provides if (isDark) DarkTripRecordColors else LightTripRecordColors,
        TripMapPalette provides if (isDark) DarkTripMapColors else LightTripMapColors,
        TripStatisticsPalette provides if (isDark) DarkTripStatisticsColors else LightTripStatisticsColors,
        content = content,
    )
}
