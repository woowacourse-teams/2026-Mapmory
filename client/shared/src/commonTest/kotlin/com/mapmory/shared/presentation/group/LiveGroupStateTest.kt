package com.mapmory.shared.presentation.group

import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.domain.group.GroupDetail
import com.mapmory.shared.domain.group.GroupInvitationPreview
import com.mapmory.shared.domain.group.GroupMemberView
import com.mapmory.shared.domain.group.GroupSummary
import com.mapmory.shared.domain.group.IssuedGroupInvitation
import com.mapmory.shared.domain.repository.GroupRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class LiveGroupStateTest {
    @Test fun departureDuringFirstListLoadKeepsOtherMemberships() = runBlocking {
        val repository = Repository().apply {
            listedGroups = listOf(GroupSummary(81, "나갈 그룹", 1, 0), GroupSummary(82, "남을 그룹", 2, 1))
            listGate = CompletableDeferred()
        }
        val state = LiveGroupState(repository)
        val reading = launch(start = CoroutineStart.UNDISPATCHED) { state.load() }
        assertNull(state.groups)
        state.removeGroup(81)
        repository.listGate!!.complete(Unit)
        reading.join()
        assertEquals(listOf(82L), state.groups?.map { it.id })
        // A later server read can include a genuinely rejoined group again.
        state.load()
        assertEquals(listOf(81L, 82L), state.groups?.map { it.id })
    }

    @Test fun listStartedBeforeDepartureCannotRestoreRemovedGroup() = runBlocking {
        val repository = Repository()
        val state = LiveGroupState(repository)
        state.create("그룹")
        val gate = CompletableDeferred<Unit>()
        repository.listGate = gate
        val reading = launch(start = CoroutineStart.UNDISPATCHED) { state.load() }
        state.removeGroup(81)
        gate.complete(Unit)
        reading.join()
        assertTrue(state.groups.orEmpty().isEmpty())
        assertNull(state.group)
    }

    @Test fun confirmedDepartureClearsInviteAndCannotBeRestoredByCancelledGesture() = runBlocking {
        val state = LiveGroupState(Repository())
        state.create("그룹")
        state.openSheet(LiveGroupSheet.INVITE)
        state.issueInvitation()
        state.gestureBack()
        state.removeGroup(81)
        assertNull(state.group)
        assertNull(state.sheet)
        assertNull(state.invitation)
        assertTrue(state.groups.orEmpty().isEmpty())
        assertFalse(state.gestureForward())
    }

    @Test fun refreshedMembershipClosesGroupThatIsNoLongerAccessible() = runBlocking {
        val state = LiveGroupState(Repository())
        state.openGroup(81)
        state.openSheet(LiveGroupSheet.LEAVE)
        state.load() // Repository returns no memberships.
        assertNull(state.group)
        assertNull(state.sheet)
    }

    @Test fun creationUsesServerIdAndRefreshKeepsCreatedGroup() = runBlocking {
        val repository = Repository()
        val state = LiveGroupState(repository)
        state.load()
        state.openSheet(LiveGroupSheet.CREATE)
        state.create("  우리 그룹  ")
        assertEquals("우리 그룹", repository.savedName)
        assertEquals(81, state.group?.id)
        assertEquals("우리 그룹", state.groups?.single()?.name)
        assertEquals(1, state.group?.members?.size)
        assertNull(state.sheet)
        state.back()
        state.load()
        assertEquals(81, state.groups?.single()?.id)
    }

    @Test fun failedCreationRequiresCheckingListBeforeAnotherWrite() = runBlocking {
        val repository = Repository().apply { createError = IllegalStateException("response lost") }
        val state = LiveGroupState(repository)
        state.openSheet(LiveGroupSheet.CREATE)
        state.create("우리 그룹")
        state.create("우리 그룹")
        assertEquals(1, repository.createCalls)
        assertTrue(state.creationUncertain)
        assertNotNull(state.error)
        state.reviewCreationResult()
        assertFalse(state.creationUncertain)
        assertNull(state.sheet)
    }

    @Test fun validationFailureKeepsSheetAndAllowsCorrectedName() = runBlocking {
        val repository = Repository().apply { createError = error("INVALID_GROUP_NAME", 400) }
        val state = LiveGroupState(repository)
        state.openSheet(LiveGroupSheet.CREATE)
        state.create("잘못된 이름")
        assertFalse(state.creationUncertain)
        assertEquals(LiveGroupSheet.CREATE, state.sheet)
        repository.createError = null
        state.create("수정한 이름")
        assertEquals("수정한 이름", state.group?.name)
    }

    @Test fun repeatedTapsDoNotCreateMultipleGroupsOrLeaveDuringRequest() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repository = Repository().apply { createGate = gate }
        val state = LiveGroupState(repository)
        state.openSheet(LiveGroupSheet.CREATE)
        val first = launch(start = CoroutineStart.UNDISPATCHED) { state.create("우리 그룹") }
        assertTrue(state.busy)
        state.create("중복 그룹")
        state.dismissSheet()
        assertTrue(state.back())
        assertEquals(LiveGroupSheet.CREATE, state.sheet)
        assertEquals(1, repository.createCalls)
        gate.complete(Unit)
        first.join()
        assertFalse(state.busy)
        assertEquals("우리 그룹", state.group?.name)
    }

    @Test fun invitationPreviewAndAcceptanceUseActualCodeAndServerGroup() = runBlocking {
        val repository = Repository()
        val state = LiveGroupState(repository)
        state.openSheet(LiveGroupSheet.JOIN)
        state.checkInvitation("ABC2D3")
        assertEquals("초대 그룹", state.preview?.groupName)
        state.acceptInvitation("ABC2D3")
        assertEquals("ABC2D3", repository.acceptedCode)
        assertEquals(81, state.group?.id)
        state.openSheet(LiveGroupSheet.INVITE)
        state.issueInvitation()
        state.issueInvitation()
        assertEquals("ABC2D3", state.invitation?.code)
        assertEquals(1, repository.issueCalls)
    }

    @Test fun changingCodeInvalidatesPreviewAndExpiredInviteCannotBeAccepted() = runBlocking {
        val repository = Repository()
        val state = LiveGroupState(repository)
        state.openSheet(LiveGroupSheet.JOIN)
        state.checkInvitation("ABC2D3")
        state.resetPreview()
        state.acceptInvitation("ZZZ2D3")
        assertNull(repository.acceptedCode)
        repository.previewError = error("INVITATION_NOT_FOUND", 404)
        state.checkInvitation("ZZZ2D3")
        assertNull(state.preview)
        assertTrue(state.error.orEmpty().contains("만료"))
    }

    @Test fun renameUpdatesListAndCancelledGestureRestoresDetailAndInvite() = runBlocking {
        val state = LiveGroupState(Repository())
        state.load()
        state.openGroup(81)
        state.openSheet(LiveGroupSheet.RENAME)
        state.rename("변경된 이름")
        assertEquals("변경된 이름", state.group?.name)
        assertEquals("변경된 이름", state.groups?.single()?.name)
        state.openSheet(LiveGroupSheet.INVITE)
        state.issueInvitation()
        val invitation = state.invitation
        assertTrue(state.gestureBack())
        assertNull(state.sheet)
        assertTrue(state.gestureForward())
        assertEquals(LiveGroupSheet.INVITE, state.sheet)
        assertEquals(invitation, state.invitation)
        state.dismissSheet()
        assertTrue(state.gestureBack())
        assertNull(state.group)
        assertTrue(state.gestureForward())
        assertEquals("변경된 이름", state.group?.name)
    }

    private class Repository : GroupRepository {
        var listedGroups: List<GroupSummary>? = null
        var savedName = "우리 그룹"
        var createError: Throwable? = null
        var previewError: Throwable? = null
        var createGate: CompletableDeferred<Unit>? = null
        var listGate: CompletableDeferred<Unit>? = null
        var createCalls = 0
        var issueCalls = 0
        var acceptedCode: String? = null
        private var created = false
        private fun detail() = GroupDetail(81, savedName, listOf(GroupMemberView("나", true, true)))
        override suspend fun leaveGroup(groupId: Long) = Result.success(Unit)
        override suspend fun groups(): Result<List<GroupSummary>> {
            val snapshot = listedGroups ?: if (created) listOf(GroupSummary(81, savedName, 1, 0)) else emptyList()
            listGate?.await()
            return Result.success(snapshot)
        }
        override suspend fun createGroup(name: String): Result<GroupDetail> {
            createCalls++
            createGate?.await()
            createError?.let { return Result.failure(it) }
            savedName = name
            created = true
            return Result.success(detail())
        }
        override suspend fun getGroup(groupId: Long) = Result.success(detail())
        override suspend fun renameGroup(groupId: Long, name: String): Result<GroupDetail> {
            savedName = name
            return Result.success(detail())
        }
        override suspend fun createInvitation(groupId: Long): Result<IssuedGroupInvitation> {
            issueCalls++
            return Result.success(IssuedGroupInvitation("token", "ABC2D3", LocalDateTime(2026, 10, 28, 12, 0)))
        }
        override suspend fun previewInvitation(tokenOrCode: String): Result<GroupInvitationPreview> =
            previewError?.let { Result.failure(it) } ?: Result.success(GroupInvitationPreview("초대 그룹", 1, false))
        override suspend fun acceptInvitation(tokenOrCode: String): Result<GroupDetail> {
            acceptedCode = tokenOrCode
            return Result.success(detail())
        }
    }

    private fun error(code: String, status: Int) = MapmoryApiException(status, code, null, null, null, emptyList())
}
