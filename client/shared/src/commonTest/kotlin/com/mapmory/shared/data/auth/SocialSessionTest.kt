package com.mapmory.shared.data.auth

import com.mapmory.shared.data.remote.MapmoryApiException
import kotlinx.coroutines.*
import kotlin.test.*

class SocialSessionTest {
    private val guest = AuthTokens("guest", "guest-refresh")
    private val social = AuthTokens("social", "social-refresh")
    private class Store(var tokens: AuthTokens? = null) : AuthTokenStore {
        var failRead = false
        var failWrite = false
        var pending: String? = null
        var recent: String? = null
        var allowClear = false
        override fun recentLoginProvider() = AuthPendingRead(recent)
        override fun saveRecentLoginProvider(provider: String) { recent = provider }
        var writes = 0
        override fun load(): AuthTokens? { check(!failRead); return tokens }
        override fun save(tokens: AuthTokens) { writes++; check(!failWrite); this.tokens = tokens }
        override fun clear() { check(allowClear) { "No implicit account deletion" }; tokens = null }
        override fun pendingProvider() = AuthPendingRead(pending)
        override fun markPendingProvider(provider: String?) { pending = provider }
    }
    private inner class Gateway : AuthGateway {
        var exchangeCalls = 0
        var refreshCalls = 0
        var guestCalls = 0
        var memberFailure = false
        var exchangeFailure = false
        var expired = false
        var invalidRefresh = false
        var bearer: String? = null
        var memberProvider = MemberProvider.GOOGLE
        var exchangeBlock: (suspend () -> Unit)? = null
        override suspend fun loginAsGuest(): Result<AuthTokens> { guestCalls++; return Result.success(guest) }
        override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
            refreshCalls++
            return if (invalidRefresh) Result.failure(apiError("INVALID_REFRESH_TOKEN")) else Result.success(guest)
        }
        override suspend fun socialLogin(provider: MemberProvider, idToken: String, accessToken: String?): Result<SocialSessionTokens> {
            exchangeCalls++; bearer = accessToken
            exchangeBlock?.invoke()
            if (expired) return Result.failure(apiError("EXPIRED_ACCESS_TOKEN"))
            if (exchangeFailure) return Result.failure(IllegalStateException("response lost"))
            return Result.success(SocialSessionTokens(social, true))
        }
        override suspend fun logout(refreshToken: String) = Result.success(Unit)
        override suspend fun member(accessToken: String): Result<AccountMember> = if (memberFailure)
            Result.failure(IllegalStateException("offline")) else Result.success(AccountMember("닉네임", memberProvider))
    }

    @Test fun recentLoginMethodSurvivesLogoutAndSessionRecreation() = runBlocking {
        val store = Store().apply { allowClear = true }
        val gateway = Gateway()
        val session = GuestSessionManager(gateway, store)
        try {
            session.login(MemberProvider.GOOGLE, "proof").getOrThrow()
            assertEquals("GOOGLE", store.recent)
            assertEquals(MemberProvider.GOOGLE, session.state.value.recentLoginProvider)
            session.logout().getOrThrow()
            assertEquals(MemberProvider.GOOGLE, session.state.value.recentLoginProvider)
            assertEquals("GOOGLE", store.recent)
        } finally { session.close() }
        val recreated = GuestSessionManager(gateway, store)
        try { assertEquals(MemberProvider.GOOGLE, recreated.state.value.recentLoginProvider) }
        finally { recreated.close() }
    }

    @Test fun failedLoginDoesNotReplaceRecentSuccessfulMethod() = runBlocking {
        val store = Store().apply { recent = "KAKAO" }
        val gateway = Gateway().apply { exchangeFailure = true }
        val session = GuestSessionManager(gateway, store)
        try {
            assertTrue(session.login(MemberProvider.GOOGLE, "proof").isFailure)
            assertEquals("KAKAO", store.recent)
            assertEquals(MemberProvider.KAKAO, session.state.value.recentLoginProvider)
        } finally { session.close() }
    }

    @Test fun successfulSessionRestoreBackfillsRecentMethodAndGuestRestoreKeepsIt() = runBlocking {
        val store = Store(social)
        val gateway = Gateway()
        val session = GuestSessionManager(gateway, store)
        try {
            session.restore().getOrThrow()
            assertEquals("GOOGLE", store.recent)
            gateway.memberProvider = MemberProvider.GUEST
            session.restore().getOrThrow()
            assertEquals("GOOGLE", store.recent)
            assertEquals(MemberProvider.GOOGLE, session.state.value.recentLoginProvider)
        } finally { session.close() }
    }

    @Test fun loginSavesNewTokensAndClearsAccountDataBeforePublishingSuccess() = runBlocking {
        val store = Store(); val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        var invalidations = 0
        session.onAccountChanged = { invalidations++; assertTrue(session.state.value.transitioning) }
        try {
            val member = session.login(MemberProvider.GOOGLE, "provider-proof").getOrThrow()
            assertEquals(MemberProvider.GOOGLE, member.provider)
            assertEquals("guest", gateway.bearer)
            assertEquals(social, store.tokens)
            assertNull(store.pending)
            assertEquals(2, invalidations)
            assertEquals(1, session.state.value.generation)
            assertTrue(session.state.value.guestAbandoned)
            assertFalse(session.state.value.pendingLogin)
        } finally { session.close() }
    }

    @Test fun saveFailureKeepsNewTokenAndRetriesOnlyPersistence() = runBlocking {
        val store = Store(); val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        try {
            session.ensureAuthenticated().getOrThrow()
            store.failWrite = true
            assertTrue(session.login(MemberProvider.GOOGLE, "proof").isFailure)
            assertEquals("social", session.getAccessToken())
            assertEquals("GOOGLE", store.pending)
            assertTrue(session.withAuthenticatedSession<Unit> { error("Must not run") }.isFailure)
            store.failWrite = false
            session.resumeLogin().getOrThrow()
            assertEquals(1, gateway.exchangeCalls)
            assertEquals(social, store.tokens)
            assertEquals(0, gateway.refreshCalls)
        } finally { session.close() }
    }

    @Test fun memberCheckFailureDoesNotExchangeAgain() = runBlocking {
        val gateway = Gateway(); val session = GuestSessionManager(gateway, Store())
        try {
            gateway.memberFailure = true
            assertTrue(session.login(MemberProvider.GOOGLE, "proof").isFailure)
            assertEquals("member_check", session.state.value.stage)
            gateway.memberFailure = false
            session.resumeLogin().getOrThrow()
            assertEquals(1, gateway.exchangeCalls)
        } finally { session.close() }
    }

    @Test fun lostResponseRequiresExplicitResumeAndNeverCreatesAnotherGuest() = runBlocking {
        val gateway = Gateway(); val session = GuestSessionManager(gateway, Store())
        try {
            gateway.exchangeFailure = true
            assertTrue(session.login(MemberProvider.GOOGLE, "proof").isFailure)
            assertTrue(session.state.value.pendingLogin)
            assertEquals(1, gateway.exchangeCalls)
            repeat(4) { assertTrue(session.withAuthenticatedSession { Result.success(Unit) }.isFailure) }
            gateway.exchangeFailure = false
            session.resumeLogin().getOrThrow()
            assertEquals(2, gateway.exchangeCalls)
            assertEquals(1, gateway.guestCalls)
        } finally { session.close() }
    }

    @Test fun pendingServerTransitionSurvivesProcessRecreationWithoutNewGuest() = runBlocking {
        val store = Store(social).apply { pending = "GOOGLE:saved" }
        val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        try {
            assertTrue(session.ensureAuthenticated().isFailure)
            session.restore().getOrThrow()
            assertEquals(0, gateway.guestCalls)
            assertEquals(0, gateway.exchangeCalls)
            assertEquals(1, session.state.value.generation)
            assertNull(store.pending)
        } finally { session.close() }
    }

    @Test fun oldGuestAccessReportingSocialMemberIsNotProofThatNewRefreshWasSaved() = runBlocking {
        val store = Store(guest).apply { pending = "GOOGLE" }
        val gateway = Gateway() // The server already promoted this guest: /me would say GOOGLE.
        val session = GuestSessionManager(gateway, store)
        try {
            assertTrue(session.restore().isFailure)
            assertTrue(session.state.value.pendingLogin)
            assertEquals(guest, store.tokens)
            assertEquals(0, session.state.value.generation)
            assertEquals(0, gateway.guestCalls)
        } finally { session.close() }
    }

    @Test fun pendingRecoveryReadFailureCanRetryReadingInsteadOfCreatingGuest() = runBlocking {
        val store = Store(social).apply { pending = "GOOGLE:saved"; failRead = true }
        val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        try {
            assertTrue(session.restore().isFailure)
            assertTrue(session.state.value.pendingLogin)
            store.failRead = false
            session.resumeLogin().getOrThrow()
            assertEquals(0, gateway.guestCalls)
            assertNull(store.pending)
        } finally { session.close() }
    }

    @Test fun storageReadFailureNeverMintsGuest() = runBlocking {
        val store = Store().apply { failRead = true }
        val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        try {
            assertTrue(session.login(MemberProvider.GOOGLE, "proof").isFailure)
            assertEquals(0, gateway.guestCalls)
            assertEquals(0, gateway.exchangeCalls)
        } finally { session.close() }
    }

    @Test fun activeOperationAndQueuedSaveBothPreventAccountMutation() = runBlocking {
        val gateway = Gateway(); val session = GuestSessionManager(gateway, Store())
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        try {
            val request = async { session.withAuthenticatedSession { entered.complete(Unit); release.await(); Result.success(Unit) } }
            entered.await()
            assertIs<AccountBusyException>(session.login(MemberProvider.GOOGLE, "proof").exceptionOrNull())
            assertEquals(0, gateway.exchangeCalls)
            release.complete(Unit); request.await()
            session.canChangeAccount = { false }
            assertIs<AccountBusyException>(session.login(MemberProvider.GOOGLE, "proof").exceptionOrNull())
            Unit
        } finally { release.complete(Unit); session.close() }
    }

    @Test fun duplicateLoginAndNewRequestsAreBlockedDuringExchange() = runBlocking {
        val gateway = Gateway(); val session = GuestSessionManager(gateway, Store())
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        gateway.exchangeBlock = { entered.complete(Unit); release.await() }
        try {
            val login = async { session.login(MemberProvider.GOOGLE, "proof") }
            entered.await()
            assertIs<AccountTransitionException>(session.login(MemberProvider.GOOGLE, "proof").exceptionOrNull())
            assertIs<AccountTransitionException>(session.withAuthenticatedSession<Unit> { error("Wrong account") }.exceptionOrNull())
            release.complete(Unit); login.await().getOrThrow()
            assertEquals(1, gateway.exchangeCalls)
        } finally { release.complete(Unit); session.close() }
    }

    @Test fun invalidRefreshNeedsExplicitRecoveryConsentAndNeverMintsGuest() = runBlocking {
        val gateway = Gateway().apply { invalidRefresh = true }
        val session = GuestSessionManager(gateway, Store(guest))
        try {
            assertTrue(session.login(MemberProvider.GOOGLE, "proof").isFailure)
            assertEquals(0, gateway.exchangeCalls)
            assertTrue(session.state.value.recoveryRequired)
            session.login(MemberProvider.GOOGLE, "proof", recoverInvalidSession = true).getOrThrow()
            assertNull(gateway.bearer)
            assertEquals(0, gateway.guestCalls)
            assertEquals(1, gateway.exchangeCalls)
        } finally { session.close() }
    }

    @Test fun queueArrivingDuringSessionPreparationPreventsServerMutation() = runBlocking {
        val gateway = Gateway()
        val session = GuestSessionManager(gateway, Store())
        var checks = 0
        session.canChangeAccount = { ++checks == 1 }
        try {
            assertIs<AccountBusyException>(session.login(MemberProvider.GOOGLE, "proof").exceptionOrNull())
            assertEquals(0, gateway.exchangeCalls)
            assertFalse(session.state.value.pendingLogin)
            Unit
        } finally { session.close() }
    }

    @Test fun sessionDoesNotReplayOnTopOfHttpAuthRetry() = runBlocking {
        val gateway = Gateway().apply { expired = true }; val session = GuestSessionManager(gateway, Store())
        try {
            assertTrue(session.login(MemberProvider.KAKAO, "proof").isFailure)
            assertEquals(1, gateway.exchangeCalls)
            assertEquals(0, gateway.refreshCalls)
        } finally { session.close() }
    }

    private fun apiError(code: String) = MapmoryApiException(401, code, null, null, null, emptyList())
}
