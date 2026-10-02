package com.mapmory.shared.data.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingPreferenceTest {
    @Test
    fun `기록_소개를_확인한_상태를_다시_읽는다`() {
        val preference = MemoryOnboardingPreference(
            initialHasSeenRecordIntroduction = false,
        )

        assertFalse(preference.hasSeenRecordIntroduction())

        preference.markRecordIntroductionSeen()

        assertTrue(preference.hasSeenRecordIntroduction())
    }
}
