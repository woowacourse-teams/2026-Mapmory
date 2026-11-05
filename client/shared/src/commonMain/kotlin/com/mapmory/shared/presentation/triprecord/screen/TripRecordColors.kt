package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mapmory.shared.MapmoryColorTheme

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
    val imagePlaceholder: Color,
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

@Immutable
internal data class TripKoreaMapColors(
    val labelText: Color,
    val provinceLabelText: Color,
    val districtOverviewLabelText: Color,
    val visitedOverviewLabelText: Color,
    val background: Color,
    val visitedFill: Color,
    val unvisitedFill: Color,
    val visitedOutline: Color,
    val unvisitedOutline: Color,
    val overviewDistrictOutline: Color,
    val overviewProvinceOutline: Color,
    val labelBackground: Color,
    val labelBorder: Color,
)

private val LightTripRecordColors = TripRecordColors(
    background = Color(0xFFFFFEFF), surface = Color.White, surfaceElevated = Color(0xFFF7FAF8),
    line = Color(0xFFE1E7E3), text = Color(0xFF1F2924), muted = Color(0xFF89948E),
    accent = Color(0xFF4D9272), accentSoft = Color(0xFFE9F2ED), danger = Color(0xFFC94C57),
    photoRecommendText = Color(0xFFBB4D56), photoRecommendBackground = Color(0xFFFFF1F1),
    photoRecommendBorder = Color(0xFFDB6A70), photoGalleryBackground = Color(0xFFF3F7F4),
    photoGalleryBorder = Color(0xFFB7C8BC), pageBackground = Color(0xFFFFFEFF),
    softSurface = Color(0xFFF0F4F1), imagePlaceholder = Color(0xFFE4E4E4), border = Color(0xFFE4E9E6), primary = Color(0xFF4D9272),
    primarySoft = Color(0xFFE9F2ED), secondaryAccent = Color(0xFF4A896B), onPrimary = Color.White,
    headingText = Color(0xFF1F2924), bodyText = Color(0xFF5F6E66), secondaryText = Color(0xFF89948E),
    navigationDivider = Color(0xFFE1E7E3), navigationUnselected = Color(0xFF9AA59F),
    navigationSelectedLabel = Color(0xFF5F6E66), contentOnMedia = Color.White,
    mediaScrim = Color.Black.copy(alpha = 0.62f), metadataDateBackground = Color(0xFFF0F4F1),
)

private val DarkTripRecordColors = TripRecordColors(
    background = Color(0xFF121518), surface = Color(0xFF1A1E22), surfaceElevated = Color(0xFF102A32),
    line = Color(0xFF1B363E), text = Color(0xFFE9F4F2), muted = Color(0xFF81999E),
    accent = Color(0xFF35C988), accentSoft = Color(0xFF123E3A), danger = Color(0xFFFF6264),
    photoRecommendText = Color.White, photoRecommendBackground = Color(0xFF382125),
    photoRecommendBorder = Color(0xFF99555D), photoGalleryBackground = Color(0xFF1B2D26),
    photoGalleryBorder = Color(0xFF3E7960), pageBackground = Color(0xFF121518),
    softSurface = Color(0xFF1C2124), imagePlaceholder = Color(0xFF353535), border = Color(0xFF2B3135), primary = Color(0xFF35C987),
    primarySoft = Color(0xFF173B2D), secondaryAccent = Color(0xFF67D9A2), onPrimary = Color(0xFF071B12),
    headingText = Color(0xFFF1F5F3), bodyText = Color(0xFFBDC6C2), secondaryText = Color(0xFFA8B5AE),
    navigationDivider = Color(0xFF2C3431), navigationUnselected = Color(0xFF77827D),
    navigationSelectedLabel = Color(0xFFA2ADA7), contentOnMedia = Color.White,
    mediaScrim = Color.Black.copy(alpha = 0.62f), metadataDateBackground = Color(0xFF24292D),
)

private val BlueLightTripRecordColors = LightTripRecordColors.copy(
    line = Color(0xFFE0E8F0), surfaceElevated = Color(0xFFF5F8FC),
    accent = Color(0xFF2876C7), accentSoft = Color(0xFFE8F2FC),
    photoGalleryBackground = Color(0xFFF3F7FB), photoGalleryBorder = Color(0xFFC4D5E7),
    pageBackground = Color(0xFFF9FBFE), softSurface = Color(0xFFF0F5FA),
    border = Color(0xFFE0E8F0), primary = Color(0xFF2876C7), primarySoft = Color(0xFFE8F2FC),
    secondaryAccent = Color(0xFF3F78AC), headingText = Color(0xFF202D3A),
    bodyText = Color(0xFF596B7B), secondaryText = Color(0xFF8796A3),
    navigationDivider = Color(0xFFE0E8F0), navigationUnselected = Color(0xFF98A5B1),
    navigationSelectedLabel = Color(0xFF526A80),
)

private val BlueDarkTripRecordColors = DarkTripRecordColors.copy(
    background = Color(0xFF101923), surface = Color(0xFF182331), surfaceElevated = Color(0xFF203148),
    line = Color(0xFF293B50), text = Color(0xFFEAF2FB), muted = Color(0xFF8EA2B7),
    accent = Color(0xFF56B3F2), accentSoft = Color(0xFF18344F),
    photoGalleryBackground = Color(0xFF1A2A3A), photoGalleryBorder = Color(0xFF35516B),
    pageBackground = Color(0xFF101923), softSurface = Color(0xFF1B2938),
    border = Color(0xFF2C3B4D), primary = Color(0xFF56B3F2), primarySoft = Color(0xFF193650),
    secondaryAccent = Color(0xFF80C6F5), onPrimary = Color(0xFF071725),
    headingText = Color(0xFFF0F5FB), bodyText = Color(0xFFC0CEDB), secondaryText = Color(0xFFA7B9CB),
    navigationDivider = Color(0xFF2A3847), navigationUnselected = Color(0xFF7E8F9F),
    navigationSelectedLabel = Color(0xFFA9C3D9),
)

private val LightTripMapColors = TripMapColors(
    logoText = Color(0xFF1F2924), scopeBackground = Color(0xFFF1F5F2), scopeBorder = Color(0xFFDCE7E0),
    scopeSelectedBackground = Color.White, scopeSelectedText = Color(0xFF2D4539),
    scopeUnselectedText = Color(0xFF7A8880), tagBackground = Color(0xFFF7FAF8),
    tagText = Color(0xFF6B786F), tagSelectedText = Color.White, dashboardBadgeText = Color(0xFF4A896B),
    globeBackground = Color(0xFFFFFEFF), globeOuterGlow = Color(0xFF789587).copy(alpha = 0.08f),
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

private val BlueLightTripMapColors = LightTripMapColors.copy(
    scopeBackground = Color(0xFFF1F6FC), scopeBorder = Color(0xFFDDE8F3),
    scopeSelectedText = Color(0xFF244A70), scopeUnselectedText = Color(0xFF71869B),
    tagBackground = Color(0xFFF4F8FC), tagText = Color(0xFF61778B),
    dashboardBadgeText = Color(0xFF2876C7),
    globeOuterGlow = Color(0xFF5C93C8).copy(alpha = 0.09f),
    globeGradientCenter = Color(0xFFF9FBFE), globeGradientMiddle = Color(0xFFF0F5FA),
    globeGradientEdge = Color(0xFFE5EDF6), globeVisitedFill = Color(0xFF2876C7),
    globeUnvisitedFill = Color(0xFFD9E4EF),
    globeVisitedOutline = Color(0xFF1C5E9B).copy(alpha = 0.72f),
    globeUnvisitedOutline = Color(0xFFAABBCD).copy(alpha = 0.78f),
)

private val BlueDarkTripMapColors = DarkTripMapColors.copy(
    scopeBackground = Color(0xFF172333), scopeBorder = Color(0xFF2E435B),
    scopeSelectedBackground = Color(0xFF243A52), scopeSelectedText = Color(0xFFE9F3FC),
    scopeUnselectedText = Color(0xFF9BAFC2), tagBackground = Color(0xFF1A2B3D),
    tagText = Color(0xFFC0CFDE), tagSelectedText = Color(0xFF071725),
    dashboardBadgeText = Color(0xFF9BD4F8),
    globeBackground = Color(0xFF101923), globeOuterGlow = Color(0xFF70BDF2).copy(alpha = 0.07f),
    globeGradientCenter = Color(0xFF273B51), globeGradientMiddle = Color(0xFF1B2B3D),
    globeGradientEdge = Color(0xFF101923), globeVisitedFill = Color(0xFF56B3F2),
    globeUnvisitedFill = Color(0xFF2B3A4D),
    globeVisitedOutline = Color(0xFF9BD4F8).copy(alpha = 0.82f),
    globeUnvisitedOutline = Color(0xFF7891AA).copy(alpha = 0.6f),
    globeHighlightCenter = Color.White.copy(alpha = 0.1f),
    globeHighlightMiddle = Color.White.copy(alpha = 0.03f),
)

private val LightTripStatisticsColors = TripStatisticsColors(
    divider = Color(0xFFE6EBE8), mapBorder = Color(0xFFE1E7E3),
    mapLand = Color(0xFFDCE4DF), mapOutline = Color(0xFFC8D3CC),
)

private val DarkTripStatisticsColors = TripStatisticsColors(
    divider = Color(0xFF30363A), mapBorder = Color(0xFF343B40),
    mapLand = Color(0xFF293039), mapOutline = Color(0xFF424B53),
)

private val BlueLightTripStatisticsColors = LightTripStatisticsColors.copy(
    divider = Color(0xFFE2EAF2), mapBorder = Color(0xFFDFE8F1),
    mapLand = Color(0xFFD9E4EF), mapOutline = Color(0xFFBCCCDC),
)

private val BlueDarkTripStatisticsColors = DarkTripStatisticsColors.copy(
    divider = Color(0xFF2D3C4D), mapBorder = Color(0xFF304155),
    mapLand = Color(0xFF26374A), mapOutline = Color(0xFF3B5066),
)

private val LightTripKoreaMapColors = TripKoreaMapColors(
    labelText = Color(0xFF6B786F), provinceLabelText = Color(0xFF34443A),
    districtOverviewLabelText = Color(0xFF5D6E62), visitedOverviewLabelText = Color(0xFF07170F),
    background = Color(0xFFFAFCFB), visitedFill = Color(0xFF4D9272),
    unvisitedFill = Color(0xFFE7EFE9), visitedOutline = Color(0xFF2F7659).copy(alpha = 0.50f),
    unvisitedOutline = Color(0xFFA7B9AC).copy(alpha = 0.70f),
    overviewDistrictOutline = Color(0xFFBECAC1).copy(alpha = 0.70f),
    overviewProvinceOutline = Color(0xFF526157).copy(alpha = 0.70f),
    labelBackground = Color(0xFFF9FCFA).copy(alpha = 0.96f),
    labelBorder = Color(0xFFD5E2D9),
)

private val DarkTripKoreaMapColors = TripKoreaMapColors(
    labelText = Color(0xFF7085A8), provinceLabelText = Color(0xFFE0E9E2),
    districtOverviewLabelText = Color(0xFFAAB6AD), visitedOverviewLabelText = Color(0xFFE0E9E2),
    background = Color(0xFF121518), visitedFill = Color(0xFF35C987),
    unvisitedFill = Color(0xFF1B2536), visitedOutline = Color(0xFF8AEBC1).copy(alpha = 0.60f),
    unvisitedOutline = Color(0xFF65738A).copy(alpha = 0.70f),
    overviewDistrictOutline = Color(0xFF36453D).copy(alpha = 0.70f),
    overviewProvinceOutline = Color(0xFF9AAFA0).copy(alpha = 0.70f),
    labelBackground = Color(0xFF121B18).copy(alpha = 0.94f),
    labelBorder = Color(0xFF3D6653),
)

private val BlueLightTripKoreaMapColors = LightTripKoreaMapColors.copy(
    labelText = Color(0xFF66798D), provinceLabelText = Color(0xFF28425C),
    districtOverviewLabelText = Color(0xFF58728A), visitedOverviewLabelText = Color(0xFF143552),
    visitedFill = Color(0xFF2876C7), unvisitedFill = Color(0xFFE6EEF7),
    visitedOutline = Color(0xFF1C5E9B).copy(alpha = 0.52f),
    unvisitedOutline = Color(0xFFAABBCD).copy(alpha = 0.72f),
    overviewDistrictOutline = Color(0xFFBCCCDC).copy(alpha = 0.72f),
    overviewProvinceOutline = Color(0xFF526A80).copy(alpha = 0.72f),
    labelBackground = Color(0xFFF8FBFE).copy(alpha = 0.96f),
    labelBorder = Color(0xFFD1DFEC),
)

private val BlueDarkTripKoreaMapColors = DarkTripKoreaMapColors.copy(
    labelText = Color(0xFF8EA9C5), provinceLabelText = Color(0xFFE4EEF8),
    districtOverviewLabelText = Color(0xFFB0C4D7), visitedOverviewLabelText = Color(0xFFEAF4FC),
    background = Color(0xFF101923), visitedFill = Color(0xFF56B3F2),
    unvisitedFill = Color(0xFF1C2B3D), visitedOutline = Color(0xFFA2D9FC).copy(alpha = 0.62f),
    unvisitedOutline = Color(0xFF7087A0).copy(alpha = 0.72f),
    overviewDistrictOutline = Color(0xFF34495E).copy(alpha = 0.72f),
    overviewProvinceOutline = Color(0xFF91A8BE).copy(alpha = 0.72f),
    labelBackground = Color(0xFF162333).copy(alpha = 0.96f),
    labelBorder = Color(0xFF3A5874),
)

internal val TripRecordPalette = staticCompositionLocalOf { LightTripRecordColors }
internal val TripMapPalette = staticCompositionLocalOf { LightTripMapColors }
internal val TripStatisticsPalette = staticCompositionLocalOf { LightTripStatisticsColors }
internal val TripKoreaMapPalette = staticCompositionLocalOf { LightTripKoreaMapColors }

@Composable
internal fun ProvideTripRecordPalettes(
    isDark: Boolean,
    colorTheme: MapmoryColorTheme = MapmoryColorTheme.GREEN,
    content: @Composable () -> Unit,
) {
    val isBlue = colorTheme == MapmoryColorTheme.BLUE
    CompositionLocalProvider(
        TripRecordPalette provides when {
            isBlue && isDark -> BlueDarkTripRecordColors
            isBlue -> BlueLightTripRecordColors
            isDark -> DarkTripRecordColors
            else -> LightTripRecordColors
        },
        TripMapPalette provides when {
            isBlue && isDark -> BlueDarkTripMapColors
            isBlue -> BlueLightTripMapColors
            isDark -> DarkTripMapColors
            else -> LightTripMapColors
        },
        TripStatisticsPalette provides when {
            isBlue && isDark -> BlueDarkTripStatisticsColors
            isBlue -> BlueLightTripStatisticsColors
            isDark -> DarkTripStatisticsColors
            else -> LightTripStatisticsColors
        },
        TripKoreaMapPalette provides when {
            isBlue && isDark -> BlueDarkTripKoreaMapColors
            isBlue -> BlueLightTripKoreaMapColors
            isDark -> DarkTripKoreaMapColors
            else -> LightTripKoreaMapColors
        },
        content = content,
    )
}
