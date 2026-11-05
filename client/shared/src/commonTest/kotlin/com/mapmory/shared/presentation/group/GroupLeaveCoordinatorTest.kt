package com.mapmory.shared.presentation.group

import com.mapmory.shared.analytics.NoOpMapmoryAnalytics
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.domain.group.*
import com.mapmory.shared.domain.repository.GroupRepository
import kotlinx.coroutines.*
import kotlin.test.*

class GroupLeaveCoordinatorTest {
    @Test fun departureNoticeIsConsumedBeforeDisplayAndDoesNotReplayAfterAnotherAction() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = Repository()
        val coordinator = GroupLeaveCoordinator(repository, owner, NoOpMapmoryAnalytics, { true }, {})
        try {
            coordinator.leave(3)
            assertEquals(3L, coordinator.takeDepartureNotice()?.groupId)
            // Recomposition, a new group, or a cancelled snackbar cannot replay the old notice.
            coordinator.membershipConfirmed(4)
            assertNull(coordinator.takeDepartureNotice())
            assertEquals(GroupLeaveStatus.LEFT, coordinator.state.value?.status)
            coordinator.leave(3)
            assertEquals(1, repository.leaves)
            coordinator.membershipConfirmed(3)
            coordinator.leave(3)
            assertEquals(3L, coordinator.takeDepartureNotice()?.groupId)
            assertNull(coordinator.takeDepartureNotice())
            assertEquals(2, repository.leaves)
        } finally { owner.cancel() }
    }

    @Test fun confirmedRejoinClearsOldDepartureResult() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = Repository()
        val coordinator = GroupLeaveCoordinator(repository, owner, NoOpMapmoryAnalytics, { true }, {})
        try {
            coordinator.leave(3)
            coordinator.membershipConfirmed(4)
            assertEquals(GroupLeaveStatus.LEFT, coordinator.state.value?.status)
            coordinator.membershipConfirmed(3)
            assertNull(coordinator.state.value)
            coordinator.leave(3)
            assertEquals(2, repository.leaves)
        } finally { owner.cancel() }
    }

    @Test fun repeatedConfirmationSendsOnceAndNavigationDoesNotCancel() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<Result<Unit>>()
        val repository = Repository().apply { leaveResponse = { response.await() } }
        val removed = mutableListOf<Long>()
        val coordinator = GroupLeaveCoordinator(repository, owner, NoOpMapmoryAnalytics, { true }, removed::add)
        try {
            screen.launch { coordinator.leave(3) }.join()
            coordinator.leave(3)
            assertTrue(coordinator.isRunning)
            assertEquals(1, repository.leaves)
            screen.cancel()
            response.complete(Result.success(Unit))
            assertEquals(GroupLeaveStatus.LEFT, coordinator.state.value?.status)
            assertEquals(listOf(3L), removed)
            coordinator.leave(3)
            assertEquals(1, repository.leaves)
        } finally { screen.cancel(); owner.cancel() }
    }

    @Test fun lostResponseRequiresReadBeforeAnotherDelete() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = Repository().apply { leaveResponse = { Result.failure(IllegalStateException("lost")) } }
        val removed = mutableListOf<Long>()
        val coordinator = GroupLeaveCoordinator(repository, owner, NoOpMapmoryAnalytics, { true }, removed::add)
        try {
            coordinator.leave(3)
            coordinator.leave(3)
            assertEquals(1, repository.leaves)
            assertEquals(GroupLeaveStatus.UNKNOWN, coordinator.state.value?.status)
            assertTrue(removed.isEmpty())
            coordinator.check(3)
            assertEquals(GroupLeaveStatus.STILL_MEMBER, coordinator.state.value?.status)
            coordinator.leave(3)
            assertEquals(2, repository.leaves)
            repository.readResponse = Result.failure(apiError(404, "GROUP_NOT_FOUND"))
            coordinator.check(3)
            assertEquals(GroupLeaveStatus.LEFT, coordinator.state.value?.status)
            assertEquals(listOf(3L), removed)
            assertEquals(2, repository.leaves)
        } finally { owner.cancel() }
    }

    @Test fun onlyExplicitMissingGroupConfirmsDeparture() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = Repository()
        val removed = mutableListOf<Long>()
        val coordinator = GroupLeaveCoordinator(repository, owner, NoOpMapmoryAnalytics, { true }, removed::add)
        try {
            for (failure in listOf(apiError(404, "NOT_FOUND"), apiError(401, "INACTIVE_MEMBER"), apiError(500, "SERVER_ERROR"))) {
                repository.readResponse = Result.failure(failure)
                coordinator.check(3)
                assertEquals(GroupLeaveStatus.UNKNOWN, coordinator.state.value?.status)
                assertTrue(removed.isEmpty())
            }
            repository.readResponse = Result.failure(apiError(404, "GROUP_NOT_FOUND"))
            coordinator.check(3)
            assertEquals(listOf(3L), removed)
        } finally { owner.cancel() }
    }

    @Test fun refusalKeepsMembershipAndAllowsRetry() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = Repository().apply { leaveResponse = { Result.failure(apiError(403, "ACCESS_DENIED")) } }
        var cleared = false
        val coordinator = GroupLeaveCoordinator(repository, owner, NoOpMapmoryAnalytics, { true }, { cleared = true })
        try {
            coordinator.leave(3)
            assertEquals(GroupLeaveStatus.FAILED, coordinator.state.value?.status)
            assertFalse(cleared)
            repository.leaveResponse = { Result.success(Unit) }
            coordinator.leave(3)
            assertTrue(cleared)
            assertEquals(2, repository.leaves)
        } finally { owner.cancel() }
    }

    @Test fun accountOrSharingGatePreventsRequestAndLateAccountResultIsDiscarded() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val response = CompletableDeferred<Result<Unit>>()
        val repository = Repository().apply { leaveResponse = { response.await() } }
        var allowed = false
        var cleared = false
        val coordinator = GroupLeaveCoordinator(repository, owner, NoOpMapmoryAnalytics, { allowed }, { cleared = true })
        try {
            coordinator.leave(3)
            assertEquals(0, repository.leaves)
            allowed = true
            coordinator.leave(3)
            assertTrue(coordinator.isRunning)
            coordinator.clearForAccountChange()
            response.complete(Result.success(Unit))
            assertNull(coordinator.state.value)
            assertFalse(cleared)
        } finally { owner.cancel() }
    }

    private class Repository : GroupRepository {
        var leaves = 0
        var leaveResponse: suspend () -> Result<Unit> = { Result.success(Unit) }
        var readResponse = Result.success(GroupDetail(3, "그룹", emptyList()))
        override suspend fun leaveGroup(groupId: Long): Result<Unit> { leaves++; return leaveResponse() }
        override suspend fun getGroup(groupId: Long) = readResponse
        override suspend fun groups(): Result<List<com.mapmory.shared.domain.group.GroupSummary>> = error("unused")
        override suspend fun createGroup(name: String): Result<GroupDetail> = error("unused")
        override suspend fun renameGroup(groupId: Long, name: String): Result<GroupDetail> = error("unused")
        override suspend fun createInvitation(groupId: Long): Result<IssuedGroupInvitation> = error("unused")
        override suspend fun previewInvitation(tokenOrCode: String): Result<GroupInvitationPreview> = error("unused")
        override suspend fun acceptInvitation(tokenOrCode: String): Result<GroupDetail> = error("unused")
    }
}

private fun apiError(status: Int, code: String) = MapmoryApiException(status, code, null, null, null, emptyList())
