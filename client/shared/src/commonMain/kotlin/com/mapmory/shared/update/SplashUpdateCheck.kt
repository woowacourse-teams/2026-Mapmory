package com.mapmory.shared.update

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Keep startup on the splash until both its minimum duration and version policy allow entry. */
internal suspend fun awaitSplashUpdateCheck(
    state: ForceUpdateState?,
    minimumDisplayMillis: Long,
) {
    delay(minimumDisplayMillis)
    snapshotFlow { state?.isBlocking != true }.first { ready -> ready }
}
