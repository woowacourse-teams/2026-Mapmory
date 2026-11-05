package com.mapmory.shared.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class SavedRecordCardEligibilityTest {
    private val foregroundStart = TimeSource.Monotonic.markNow()

    @Test
    fun currentForegroundCompletionIsEligible() {
        assertTrue(shouldShowSavedRecordCard(foregroundStart + 1.seconds, foregroundStart, true))
    }

    @Test
    fun backgroundAndStoppedCompletionsAreNotEligible() {
        assertFalse(shouldShowSavedRecordCard(foregroundStart + 1.seconds, null, false))
        assertFalse(shouldShowSavedRecordCard(foregroundStart + 1.seconds, foregroundStart, false))
    }

    @Test
    fun queuedCompletionFromBeforeMountOrResumeDoesNotAppear() {
        assertFalse(shouldShowSavedRecordCard(foregroundStart - 1.seconds, foregroundStart, true))
    }
}
