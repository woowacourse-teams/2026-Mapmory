package com.mapmory.shared

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** 설정의 앱 버전을 연속으로 눌러 팀원 기기 모드를 바꾸는 숨은 스위치 상태다. */
@Immutable
data class TeamDeviceState(
    val appVersion: String,
    val isTeamDevice: Boolean,
    val onTeamDeviceChange: (Boolean) -> Unit,
)

val LocalTeamDevice = staticCompositionLocalOf {
    TeamDeviceState(
        appVersion = "",
        isTeamDevice = false,
        onTeamDeviceChange = {},
    )
}

/** 첫 탭부터 [window] 안에 [requiredTaps]번 누르면 true를 돌려주고 다시 처음부터 센다. */
class TeamDeviceTapCounter(
    private val requiredTaps: Int = TeamDeviceRequiredTaps,
    private val window: Duration = TeamDeviceTapWindow,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var taps = 0
    private var firstTap: TimeMark? = null

    fun tap(): Boolean {
        val start = firstTap
        if (start == null || start.elapsedNow() > window) {
            firstTap = timeSource.markNow()
            taps = 0
        }
        taps += 1
        if (taps < requiredTaps) return false
        taps = 0
        firstTap = null
        return true
    }
}

const val TeamDeviceRequiredTaps = 7
val TeamDeviceTapWindow = 3.seconds
