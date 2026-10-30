package com.mapmory.shared.presentation.triprecord

import androidx.compose.ui.graphics.Color
import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.presentation.triprecord.screen.badgePalette
import com.mapmory.shared.presentation.triprecord.screen.newlyEarnedTripAchievementBadges
import com.mapmory.shared.presentation.triprecord.screen.tripAchievementBadges
import com.mapmory.shared.presentation.triprecord.screen.tripAchievementMoriExpressionIndex
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripAchievementCollectionTest {
    @Test
    fun badgesUseRecordAndDistinctRegionMilestones() {
        val badges = tripAchievementBadges(
            TripStatisticsUiModel.Empty.copy(
                recordCount = 10,
                photoCount = 500,
                koreaVisitedCount = 3,
                worldVisitedCount = 3,
                visitedCountryCodes = setOf("KR", "FR", "JP"),
            ),
        )

        assertEquals(12, badges.size)
        assertEquals(badges.size, badges.map { it.id }.toSet().size)
        assertEquals(5, badges.count { it.earned })
        assertTrue(badges.single { it.id == "records_10" }.earned)
        assertFalse(badges.single { it.id == "records_25" }.earned)
        assertTrue(badges.single { it.id == "regions_5" }.earned)
        assertEquals(5, badges.single { it.id == "regions_10" }.progress)
    }

    @Test
    fun unearnedBadgeArtworkIsFullyGrayscale() {
        assertTrue(tripAchievementBadges(TripStatisticsUiModel.Empty).none { it.earned })
        val colors = badgePalette(earned = false)
        listOf(
            colors.outerLight, colors.outer, colors.outerDark,
            colors.faceLight, colors.face, colors.faceDark,
            colors.rim, colors.ink, colors.bright,
            colors.leafLeft, colors.leafRight, colors.sparkle,
        ).forEach { color ->
            assertEquals(color.red, color.green)
            assertEquals(color.green, color.blue)
        }
    }

    @Test
    fun earnedBadgePaletteUsesMoriGreenAndCream() {
        val colors = badgePalette(earned = true)

        assertEquals(Color(0xFF6FA582), colors.outer)
        assertEquals(Color(0xFFF4E6C2), colors.face)
        assertEquals(Color(0xFF255D3C), colors.leafLeft)
        assertEquals(Color(0xFFE8754F), colors.sparkle)
    }

    @Test
    fun newlyEarnedBadgesIncludesEveryMilestoneCrossedAtOnce() {
        val previous = statistics(recordCount = 4, visitedKoreaDistrictCount = 1, visitedCountryCount = 1, codes = listOf("KR"))
        val current = statistics(recordCount = 5, visitedKoreaDistrictCount = 3, visitedCountryCount = 2, codes = listOf("KR", "FR"))

        assertEquals(
            setOf("records_5", "two_regions"),
            newlyEarnedTripAchievementBadges(previous, current).map { it.id }.toSet(),
        )
    }

    @Test
    fun moriExpressionChangesWithOverallCollectionProgress() {
        assertEquals(2, tripAchievementMoriExpressionIndex(0, 12))
        assertEquals(0, tripAchievementMoriExpressionIndex(2, 12))
        assertEquals(4, tripAchievementMoriExpressionIndex(4, 12))
        assertEquals(3, tripAchievementMoriExpressionIndex(7, 12))
        assertEquals(1, tripAchievementMoriExpressionIndex(12, 12))
    }

    private fun statistics(
        recordCount: Long,
        visitedKoreaDistrictCount: Long,
        visitedCountryCount: Long,
        codes: List<String>,
    ) = TripStatistics(
        recordCount = recordCount,
        mediaCount = 0,
        visitedCountryCount = visitedCountryCount,
        visitedKoreaDistrictCount = visitedKoreaDistrictCount,
        visitedCountryCodes = codes,
        topRegions = emptyList(),
    )
}
