package com.mapmory.shared.data.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingPreferenceTest {
    @Test
    fun `영상_미리보기_확인은_기록_소개와_독립적으로_저장한다`() {
        val preference = MemoryOnboardingPreference(initialHasSeenRecordIntroduction = false)
        assertFalse(preference.hasSeenVideoPreview())
        preference.markVideoPreviewSeen()
        preference.markVideoPreviewSeen()
        assertTrue(preference.hasSeenVideoPreview())
        assertFalse(preference.hasSeenRecordIntroduction())
    }

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
