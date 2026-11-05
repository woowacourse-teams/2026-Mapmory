package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mapmory.shared.data.settings.MapmoryThemePalette

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
    val koreaVisitedLabelText: Color,
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
    koreaVisitedLabelText = Color(0xFF07170F),
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
    koreaVisitedLabelText = Color(0xFFE0E9E2),
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

private object OceanBluePrimitives {
    val blue50 = Color(0xFFF2F7FD)
    val blue100 = Color(0xFFE4EEF9)
    val blue200 = Color(0xFFC9DDF1)
    val blue300 = Color(0xFFA9C7E6)
    val blue600 = Color(0xFF2B6FAE)
    val blue700 = Color(0xFF246DBB)
    val blue800 = Color(0xFF1F5F9E)
    val blue900 = Color(0xFF194E82)
    val navy700 = Color(0xFF2A3B50)
    val navy800 = Color(0xFF213247)
    val navy900 = Color(0xFF17283C)
    val slate500 = Color(0xFF64788C)
    val slate600 = Color(0xFF52677D)
}

private val LightOceanBlueTripRecordColors = LightTripRecordColors.copy(
    surfaceElevated = OceanBluePrimitives.blue50,
    line = OceanBluePrimitives.blue100,
    text = OceanBluePrimitives.navy900,
    muted = OceanBluePrimitives.slate500,
    accent = OceanBluePrimitives.blue700,
    accentSoft = OceanBluePrimitives.blue50,
    photoGalleryBackground = OceanBluePrimitives.blue50,
    photoGalleryBorder = OceanBluePrimitives.blue300,
    softSurface = OceanBluePrimitives.blue50,
    border = OceanBluePrimitives.blue100,
    primary = OceanBluePrimitives.blue700,
    primarySoft = OceanBluePrimitives.blue100,
    secondaryAccent = OceanBluePrimitives.blue600,
    headingText = OceanBluePrimitives.navy900,
    bodyText = OceanBluePrimitives.slate600,
    secondaryText = OceanBluePrimitives.slate500,
    navigationDivider = OceanBluePrimitives.blue100,
    navigationUnselected = OceanBluePrimitives.slate500,
    navigationSelectedLabel = OceanBluePrimitives.slate600,
    metadataDateBackground = OceanBluePrimitives.blue50,
)

private val DarkOceanBlueTripRecordColors = DarkTripRecordColors.copy(
    surface = Color(0xFF19212A),
    surfaceElevated = Color(0xFF202D3B),
    line = Color(0xFF283747),
    text = Color(0xFFECF4FB),
    muted = Color(0xFF91A4B8),
    accent = Color(0xFF83B8F3),
    accentSoft = Color(0xFF193451),
    photoGalleryBackground = Color(0xFF1B2C3D),
    photoGalleryBorder = Color(0xFF3A5979),
    softSurface = Color(0xFF1B2733),
    border = Color(0xFF2C3E51),
    primary = Color(0xFF6EA9F3),
    primarySoft = Color(0xFF18324E),
    secondaryAccent = Color(0xFF97C5F7),
    onPrimary = Color(0xFF0C1A27),
    headingText = Color(0xFFECF4FB),
    bodyText = Color(0xFFB9C8D8),
    secondaryText = Color(0xFFA7B9CB),
    navigationDivider = Color(0xFF293744),
    navigationUnselected = Color(0xFF7F8FA1),
    navigationSelectedLabel = Color(0xFFB7C7D8),
    metadataDateBackground = Color(0xFF202D3B),
)

private val LightOceanBlueTripMapColors = LightTripMapColors.copy(
    logoText = OceanBluePrimitives.navy900,
    scopeBackground = OceanBluePrimitives.blue50,
    scopeBorder = OceanBluePrimitives.blue100,
    scopeSelectedText = OceanBluePrimitives.navy800,
    scopeUnselectedText = OceanBluePrimitives.slate500,
    tagBackground = OceanBluePrimitives.blue50,
    tagText = OceanBluePrimitives.slate600,
    dashboardBadgeText = OceanBluePrimitives.blue700,
    globeOuterGlow = Color(0xFF6FA8E8).copy(alpha = 0.08f),
    globeGradientCenter = Color(0xFFFAFCFF),
    globeGradientMiddle = OceanBluePrimitives.blue50,
    globeGradientEdge = OceanBluePrimitives.blue100,
    globeVisitedFill = OceanBluePrimitives.blue600,
    globeUnvisitedFill = OceanBluePrimitives.blue100,
    globeVisitedOutline = OceanBluePrimitives.blue800.copy(alpha = 0.72f),
    globeUnvisitedOutline = OceanBluePrimitives.blue300.copy(alpha = 0.72f),
    koreaVisitedLabelText = Color.White,
)

private val DarkOceanBlueTripMapColors = DarkTripMapColors.copy(
    logoText = Color(0xFFECF4FB),
    scopeBackground = Color(0xFF1B2835),
    scopeBorder = Color(0xFF304153),
    scopeSelectedBackground = Color(0xFF293A4D),
    scopeSelectedText = Color(0xFFECF4FB),
    scopeUnselectedText = Color(0xFF97A9BC),
    tagBackground = Color(0xFF1D2A38),
    tagText = Color(0xFFBDCAD8),
    tagSelectedText = Color(0xFF0C1A27),
    dashboardBadgeText = Color(0xFF97C5F7),
    globeOuterGlow = Color(0xFF6EA9F3).copy(alpha = 0.08f),
    globeGradientCenter = Color(0xFF25364A),
    globeGradientMiddle = Color(0xFF1A293A),
    globeGradientEdge = Color(0xFF121C29),
    globeVisitedFill = Color(0xFF6EA9F3),
    globeUnvisitedFill = Color(0xFF2D3B4A),
    globeVisitedOutline = Color(0xFF9CC9F8).copy(alpha = 0.82f),
    globeUnvisitedOutline = Color(0xFF8298B0).copy(alpha = 0.58f),
    koreaVisitedLabelText = Color(0xFF0C1A27),
    globeHighlightCenter = Color.White.copy(alpha = 0.10f),
    globeHighlightMiddle = Color.White.copy(alpha = 0.035f),
)

private val LightOceanBlueTripStatisticsColors = LightTripStatisticsColors.copy(
    divider = OceanBluePrimitives.blue100,
    mapBorder = OceanBluePrimitives.blue100,
    mapLand = OceanBluePrimitives.blue100,
    mapOutline = OceanBluePrimitives.blue300,
)

private val DarkOceanBlueTripStatisticsColors = DarkTripStatisticsColors.copy(
    divider = OceanBluePrimitives.navy700,
    mapBorder = OceanBluePrimitives.navy700,
    mapLand = OceanBluePrimitives.navy800,
    mapOutline = Color(0xFF52677D),
)

internal fun tripRecordColors(isDark: Boolean, themePalette: MapmoryThemePalette): TripRecordColors =
    when (themePalette) {
        MapmoryThemePalette.MAPMORY_GREEN -> if (isDark) DarkTripRecordColors else LightTripRecordColors
        MapmoryThemePalette.OCEAN_BLUE -> if (isDark) DarkOceanBlueTripRecordColors else LightOceanBlueTripRecordColors
    }

private fun tripMapColors(isDark: Boolean, themePalette: MapmoryThemePalette): TripMapColors =
    when (themePalette) {
        MapmoryThemePalette.MAPMORY_GREEN -> if (isDark) DarkTripMapColors else LightTripMapColors
        MapmoryThemePalette.OCEAN_BLUE -> if (isDark) DarkOceanBlueTripMapColors else LightOceanBlueTripMapColors
    }

private fun tripStatisticsColors(isDark: Boolean, themePalette: MapmoryThemePalette): TripStatisticsColors =
    when (themePalette) {
        MapmoryThemePalette.MAPMORY_GREEN -> if (isDark) DarkTripStatisticsColors else LightTripStatisticsColors
        MapmoryThemePalette.OCEAN_BLUE -> if (isDark) DarkOceanBlueTripStatisticsColors else LightOceanBlueTripStatisticsColors
    }

private val LightTripKoreaMapColors = TripKoreaMapColors(
    labelText = Color(0xFF6B786F), provinceLabelText = Color(0xFF34443A),
    districtOverviewLabelText = Color(0xFF5D6E62), visitedOverviewLabelText = Color(0xFF07170F),
    visitedFill = Color(0xFF4D9272),
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
    visitedFill = Color(0xFF35C987),
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
    visitedFill = Color(0xFF56B3F2),
    unvisitedFill = Color(0xFF1C2B3D), visitedOutline = Color(0xFFA2D9FC).copy(alpha = 0.62f),
    unvisitedOutline = Color(0xFF7087A0).copy(alpha = 0.72f),
    overviewDistrictOutline = Color(0xFF34495E).copy(alpha = 0.72f),
    overviewProvinceOutline = Color(0xFF91A8BE).copy(alpha = 0.72f),
    labelBackground = Color(0xFF162333).copy(alpha = 0.96f),
    labelBorder = Color(0xFF3A5874),
)

internal val TripKoreaMapPalette = staticCompositionLocalOf { LightTripKoreaMapColors }
internal val TripRecordPalette = staticCompositionLocalOf { LightTripRecordColors }
internal val TripMapPalette = staticCompositionLocalOf { LightTripMapColors }
internal val TripStatisticsPalette = staticCompositionLocalOf { LightTripStatisticsColors }

@Composable
internal fun ProvideTripRecordPalettes(
    isDark: Boolean,
    themePalette: MapmoryThemePalette = MapmoryThemePalette.MAPMORY_GREEN,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        TripRecordPalette provides tripRecordColors(isDark, themePalette),
        TripMapPalette provides tripMapColors(isDark, themePalette),
        TripStatisticsPalette provides tripStatisticsColors(isDark, themePalette),
        TripKoreaMapPalette provides when (themePalette) {
            MapmoryThemePalette.MAPMORY_GREEN -> if (isDark) DarkTripKoreaMapColors else LightTripKoreaMapColors
            MapmoryThemePalette.OCEAN_BLUE -> if (isDark) BlueDarkTripKoreaMapColors else BlueLightTripKoreaMapColors
        },
        content = content,
    )
}
