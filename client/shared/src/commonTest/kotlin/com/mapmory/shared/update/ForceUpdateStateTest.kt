package com.mapmory.shared.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForceUpdateStateTest {
    @Test fun comparesNumericComponents() {
        assertTrue(requiresUpdate("1.9.9", "1.10.0"))
        assertTrue(requiresUpdate("0.1.8-internal", "0.1.9"))
        assertFalse(requiresUpdate("1.2", "1.2.0"))
        assertFalse(requiresUpdate("2.0.0", "1.99.99"))
        assertFalse(requiresUpdate("1.2.4", "1.2.4"))
    }

    @Test fun invalidPolicyCannotRemoveCachedRestriction() {
        val state = ForceUpdateState("1.2.4", "https://example.com")
        assertTrue(state.applyMinimumVersion("1.3.0"))
        listOf("", "-1", "1.x", "1.2.3.4", "999999999999999999999").forEach {
            assertFalse(state.applyMinimumVersion(it))
            assertTrue(state.isUpdateRequired)
        }
        state.finishCheck()
        assertTrue(state.isBlocking)
        assertTrue(state.applyMinimumVersion("0.0.0"))
        assertFalse(state.isBlocking)
    }

    @Test fun firstFailureAllowsUseButCachedPolicyStillBlocks() {
        val state = ForceUpdateState("0.1.8", "https://example.com")
        assertTrue(state.isBlocking)
        state.finishCheck()
        assertFalse(state.isBlocking)
        state.applyMinimumVersion("0.1.9")
        state.finishCheck()
        assertTrue(state.isBlocking)
    }

    @Test fun storeUpdatePromptCanBeDismissed() {
        val state = ForceUpdateState("1.2.2", "https://example.com")
        state.finishCheck()
        state.reportStoreUpdateAvailability(true)
        assertTrue(state.isStoreUpdateAvailable)
        assertFalse(state.isBlocking)
        state.dismissStoreUpdate()
        assertFalse(state.isStoreUpdateAvailable)
    }

    @Test fun recommendedUpdateCanBeDismissedUntilPolicyChanges() {
        val state = ForceUpdateState("0.1.9", "https://example.com")
        assertTrue(state.applyRecommendedVersion("0.2.0"))
        assertTrue(state.isRecommendedUpdateAvailable)

        state.dismissOptionalUpdate()
        assertFalse(state.isRecommendedUpdateAvailable)
        assertTrue(state.applyRecommendedVersion("0.2.0"))
        assertFalse(state.isRecommendedUpdateAvailable)

        assertTrue(state.applyRecommendedVersion("0.2.1"))
        assertTrue(state.isRecommendedUpdateAvailable)
    }

    @Test fun invalidRecommendedPolicyCannotClearAcceptedVersion() {
        val state = ForceUpdateState("0.1.9", "https://example.com")
        assertTrue(state.applyRecommendedVersion("0.2.0"))

        listOf("", "-1", "1.x", "1.2.3.4").forEach {
            assertFalse(state.applyRecommendedVersion(it))
            assertTrue(state.isRecommendedUpdateAvailable)
        }
    }
}
