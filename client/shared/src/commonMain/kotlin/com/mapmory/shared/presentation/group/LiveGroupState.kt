package com.mapmory.shared.presentation.group

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.NoOpMapmoryAnalytics
import com.mapmory.shared.analytics.emit
import com.mapmory.shared.analytics.reportFailure
import kotlin.time.TimeSource
import com.mapmory.shared.domain.group.GroupDetail
import com.mapmory.shared.domain.group.GroupInvitationPreview
import com.mapmory.shared.domain.group.GroupSummary as RemoteGroupSummary
import com.mapmory.shared.domain.group.IssuedGroupInvitation
import com.mapmory.shared.domain.repository.GroupRepository

internal enum class LiveGroupSheet { CREATE, JOIN, INVITE, RENAME }

/** All actions use server responses. No sample group IDs, members or invitations enter this state. */
internal class LiveGroupState(
    private val repository: GroupRepository,
    private val analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
) {
    var groups by mutableStateOf<List<RemoteGroupSummary>?>(null)
        private set
    var group by mutableStateOf<GroupDetail?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var sheet by mutableStateOf<LiveGroupSheet?>(null)
        private set
    var invitation by mutableStateOf<IssuedGroupInvitation?>(null)
        private set
    var preview by mutableStateOf<GroupInvitationPreview?>(null)
        private set
    var creationUncertain by mutableStateOf(false)
        private set

    private data class NavigationSnapshot(
        val group: GroupDetail?, val sheet: LiveGroupSheet?,
        val invitation: IssuedGroupInvitation?, val preview: GroupInvitationPreview?, val error: String?,
    )
    private var forward: NavigationSnapshot? = null

    fun gestureBack(): Boolean {
        if (busy) return true
        if (sheet == null && group == null) return false
        val snapshot = NavigationSnapshot(group, sheet, invitation, preview, error)
        back()
        forward = snapshot
        return true
    }

    fun gestureForward(): Boolean {
        if (busy) return true
        val snapshot = forward ?: return false
        group = snapshot.group; sheet = snapshot.sheet
        invitation = snapshot.invitation; preview = snapshot.preview; error = snapshot.error
        forward = null
        return true
    }

    fun openSheet(value: LiveGroupSheet) {
        if (busy) return
        forward = null
        sheet = value
        error = null
        invitation = null
        preview = null
    }

    fun dismissSheet() {
        if (busy) return
        sheet = null
        invitation = null
        preview = null
        error = null
    }

    fun back(): Boolean {
        if (busy) return true
        if (sheet != null) { dismissSheet(); return true }
        if (group != null) { group = null; error = null; return true }
        return false
    }

    fun resetPreview() { if (!busy) { preview = null; error = null } }

    suspend fun load() = perform {
        val started = TimeSource.Monotonic.markNow()
        val result = repository.groups()
        result.onSuccess { groups = it }.onFailure {
            error = message(it)
            analytics.reportFailure("group_list", it, mapOf("source" to "group_tab", "execution_mode" to "live"))
        }
        analytics.emit("group_list_loaded", mapOf(
            "source" to "group_tab", "execution_mode" to "live",
            "outcome" to if (result.isSuccess) "success" else "failure",
            "duration_ms" to started.elapsedNow().inWholeMilliseconds.toString(),
        ))
    }

    suspend fun openGroup(id: Long) = perform {
        repository.getGroup(id).onSuccess { group = it }.onFailure { error = message(it) }
    }

    suspend fun create(name: String) {
        if (creationUncertain) return
        perform {
            repository.createGroup(name.trim()).onSuccess {
                showGroup(it)
            }.onFailure {
                creationUncertain = it !is MapmoryApiException || it.statusCode >= 500
                error = if (creationUncertain) "그룹 생성 결과를 확인하지 못했어요. 다시 만들기 전에 그룹 목록을 확인해 주세요." else message(it)
            }
        }
    }

    suspend fun reviewCreationResult() = perform {
        repository.groups().onSuccess {
            groups = it
            group = null
            sheet = null
            creationUncertain = false
        }.onFailure { error = message(it) }
    }

    suspend fun rename(name: String) = perform {
        val current = group ?: return@perform
        repository.renameGroup(current.id, name.trim()).onSuccess(::showGroup).onFailure { error = message(it) }
    }

    suspend fun issueInvitation() = perform {
        val current = group ?: return@perform
        // Keep a successfully issued code visible instead of generating another on repeated taps.
        if (invitation != null) return@perform
        repository.createInvitation(current.id).onSuccess { invitation = it }.onFailure { error = message(it) }
    }

    suspend fun checkInvitation(code: String) = perform {
        preview = null
        repository.previewInvitation(code).onSuccess { preview = it }.onFailure { error = message(it) }
    }

    suspend fun acceptInvitation(code: String) = perform {
        if (preview == null) return@perform
        repository.acceptInvitation(code).onSuccess(::showGroup).onFailure { error = message(it) }
    }

    private fun showGroup(detail: GroupDetail) {
        group = detail
        val existing = groups.orEmpty()
        val albumCount = existing.firstOrNull { it.id == detail.id }?.albumCount ?: 0
        val summary = RemoteGroupSummary(detail.id, detail.name, detail.members.size.toLong(), albumCount, existing.firstOrNull { it.id == detail.id }?.coverPhoto)
        groups = if (existing.any { it.id == detail.id }) existing.map { if (it.id == detail.id) summary else it }
            else existing + summary
        sheet = null
        invitation = null
        preview = null
        creationUncertain = false
    }

    private suspend fun perform(block: suspend () -> Unit) {
        if (busy) return
        forward = null
        busy = true
        error = null
        try { block() } finally { busy = false }
    }

    private fun message(error: Throwable): String = when ((error as? MapmoryApiException)?.code) {
        "INVALID_GROUP_NAME" -> "그룹 이름은 공백을 제외하고 1~30자로 입력해 주세요."
        "SOCIAL_LOGIN_REQUIRED" -> "그룹 기능은 소셜 로그인 후 사용할 수 있어요."
        "GROUP_NOT_FOUND" -> "그룹이 없거나 참여한 그룹이 아니에요. 목록을 새로고침해 주세요."
        "INVITATION_NOT_FOUND" -> "초대 코드가 없거나 만료됐어요. 코드를 확인하거나 새 초대를 요청해 주세요."
        "GROUP_FULL" -> "그룹 정원이 가득 찼어요. 최대 30명까지 참여할 수 있어요."
        "TOO_MANY_GROUPS" -> "그룹은 최대 10개까지 만들거나 참여할 수 있어요."
        "INVITATION_RATE_LIMITED" -> "초대를 너무 자주 확인했어요. 잠시 후 다시 시도해 주세요."
        else -> "요청을 처리하지 못했어요. 다시 시도해 주세요."
    }
}
