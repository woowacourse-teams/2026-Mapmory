package com.mapmory.shared.presentation.group

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.emit
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.domain.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class GroupLeaveStatus { RUNNING, LEFT, FAILED, UNKNOWN, STILL_MEMBER }

data class GroupLeaveResult(val groupId: Long, val status: GroupLeaveStatus)

/** Navigation does not cancel a leave request. After process restart, the server group list is authoritative. */
class GroupLeaveCoordinator(
    private val repository: GroupRepository,
    private val scope: CoroutineScope,
    private val analytics: MapmoryAnalytics,
    private val canStart: () -> Boolean,
    private val onLeft: (Long) -> Unit,
) {
    private val mutableState = MutableStateFlow<GroupLeaveResult?>(null)
    val state = mutableState.asStateFlow()
    val isRunning get() = state.value?.status == GroupLeaveStatus.RUNNING
    private var accountEpoch = 0L
    private var notifiedDeparture: GroupLeaveResult? = null

    fun leave(groupId: Long) {
        if (state.value?.let { it.groupId == groupId && it.status in listOf(GroupLeaveStatus.UNKNOWN, GroupLeaveStatus.LEFT) } == true) return
        run(groupId, checkOnly = false)
    }

    /** Reads membership after response loss; never repeats a destructive request automatically. */
    fun check(groupId: Long) = run(groupId, checkOnly = true)

    private fun run(groupId: Long, checkOnly: Boolean) {
        if (isRunning || !canStart()) return
        val epoch = accountEpoch
        // Set before dispatch: sharing and account changes observe the reservation immediately.
        mutableState.value = GroupLeaveResult(groupId, GroupLeaveStatus.RUNNING)
        scope.launch {
            if (epoch != accountEpoch) return@launch
            val status = try {
                if (checkOnly) repository.getGroup(groupId).fold(
                    { GroupLeaveStatus.STILL_MEMBER },
                    { if (it.isMissingGroup()) GroupLeaveStatus.LEFT else GroupLeaveStatus.UNKNOWN },
                ) else repository.leaveGroup(groupId).fold(
                    { GroupLeaveStatus.LEFT },
                    { when {
                        it.isMissingGroup() -> GroupLeaveStatus.LEFT
                        it is MapmoryApiException && it.statusCode in 400..499 -> GroupLeaveStatus.FAILED
                        else -> GroupLeaveStatus.UNKNOWN
                    } },
                )
            } catch (error: CancellationException) {
                if (epoch == accountEpoch) mutableState.value = GroupLeaveResult(groupId, GroupLeaveStatus.UNKNOWN)
                throw error
            } catch (_: Exception) {
                GroupLeaveStatus.UNKNOWN
            }
            if (epoch != accountEpoch) return@launch
            if (status == GroupLeaveStatus.LEFT) onLeft(groupId)
            mutableState.value = GroupLeaveResult(groupId, status)
            analytics.emit(if (checkOnly) "group_leave_checked" else "group_leave_result", mapOf(
                "execution_mode" to "live",
                "result" to when (status) {
                    GroupLeaveStatus.LEFT -> "success"
                    GroupLeaveStatus.STILL_MEMBER -> "still_member"
                    GroupLeaveStatus.FAILED -> "failure"
                    else -> "unknown"
                },
            ))
        }
    }

    /** Consume before displaying: a suspended/cancelled snackbar must not replay this result. */
    fun takeDepartureNotice(): GroupLeaveResult? {
        val result = state.value ?: return null
        if (result.status != GroupLeaveStatus.LEFT || notifiedDeparture === result) return null
        notifiedDeparture = result
        return result
    }

    fun dismiss() { if (!isRunning) mutableState.value = null }

    /** A later successful group read or invitation acceptance supersedes the previous departure. */
    fun membershipConfirmed(groupId: Long) {
        if (!isRunning && state.value?.groupId == groupId) mutableState.value = null
    }

    fun clearForAccountChange() {
        accountEpoch++
        notifiedDeparture = null
        mutableState.value = null
    }
}

internal fun Throwable.isMissingGroup() = this is MapmoryApiException && statusCode == 404 && code == "GROUP_NOT_FOUND"
