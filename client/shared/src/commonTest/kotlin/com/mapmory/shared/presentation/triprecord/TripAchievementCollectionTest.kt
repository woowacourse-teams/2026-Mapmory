package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.presentation.triprecord.screen.badgePalette
import com.mapmory.shared.presentation.triprecord.screen.tripAchievementBadges
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
        tripAchievementBadges(TripStatisticsUiModel.Empty).forEach { badge ->
            assertFalse(badge.earned)
            val colors = badgePalette(badge.id, earned = false)
            listOf(
                colors.outerLight, colors.outer, colors.outerDark,
                colors.faceLight, colors.face, colors.faceDark,
                colors.rim, colors.ink, colors.bright,
                colors.ribbonLeft, colors.ribbonRight, colors.sparkle,
            ).forEach { color ->
                assertEquals(color.red, color.green)
                assertEquals(color.green, color.blue)
            }
        }
    }
}
