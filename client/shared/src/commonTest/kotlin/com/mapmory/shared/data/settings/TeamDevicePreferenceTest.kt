package com.mapmory.shared.data.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamDevicePreferenceTest {
    @Test
    fun `저장한_팀원_기기_여부를_다시_읽는다`() {
        val preference = MemoryTeamDevicePreference()

        assertFalse(preference.loadIsTeamDevice())

        preference.saveIsTeamDevice(true)

        assertTrue(preference.loadIsTeamDevice())
    }
}
