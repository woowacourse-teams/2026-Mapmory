package com.mapmory.shared.update

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse

class SplashUpdateCheckTest {
    @Test
    fun initialCheckKeepsSplashUntilFinished() = runBlocking {
        withTimeout(5_000) {
            val state = ForceUpdateState("1.2.4", "https://example.com")
            val startup = async(start = CoroutineStart.UNDISPATCHED) { awaitSplashUpdateCheck(state, 0) }
            assertFalse(startup.isCompleted)

            state.finishCheck()
            Snapshot.sendApplyNotifications()
            startup.await()
        }
    }

    @Test
    fun requiredUpdateKeepsSplashAfterFetchAndRetryUntilPolicyAllowsEntry() = runBlocking {
        withTimeout(5_000) {
            val state = ForceUpdateState("1.2.4", "https://example.com")
            state.applyMinimumVersion("1.3.0")
            state.finishCheck()
            val startup = async(start = CoroutineStart.UNDISPATCHED) { awaitSplashUpdateCheck(state, 0) }
            assertFalse(startup.isCompleted)

            state.applyMinimumVersion("invalid")
            Snapshot.sendApplyNotifications()
            assertFalse(startup.isCompleted)

            state.applyMinimumVersion("1.2.4")
            Snapshot.sendApplyNotifications()
            startup.await()
        }
    }

    @Test
    fun firstOfflineCheckWithoutCachedRestrictionAllowsEntry() = runBlocking {
        val state = ForceUpdateState("1.2.4", "https://example.com")
        state.finishCheck()
        withTimeout(5_000) { awaitSplashUpdateCheck(state, 0) }
    }

    @Test
    fun noVersionAdapterAllowsPreviewAndInMemoryApp() = runBlocking {
        withTimeout(5_000) { awaitSplashUpdateCheck(null, 0) }
    }
}
