package com.mapmory.shared.data.auth

import kotlinx.coroutines.*
import kotlin.test.*

class LogoutSessionTest {
    private class Store : AuthTokenStore {
        var tokens: AuthTokens? = AuthTokens("social", "social-refresh")
        var marker: String? = null
        var failClear = false
        var failMarker = false
        override fun load() = tokens
        override fun save(tokens: AuthTokens) { this.tokens = tokens }
        override fun clear() { check(!failClear); tokens = null }
        override fun pendingProvider() = AuthPendingRead(marker)
        override fun markPendingProvider(provider: String?) { check(!failMarker); marker = provider }
    }
    private class Gateway : AuthGateway {
        var withdrawalCalls = 0
        var failWithdrawal = false
        override suspend fun withdraw(accessToken: String, identityToken: String?, authorizationCode: String?): Result<Unit> {
            withdrawalCalls++
            return if (failWithdrawal) Result.failure(IllegalStateException("response lost")) else Result.success(Unit)
        }
        var logoutCalls = 0
        var guestCalls = 0
        var refreshCalls = 0
        var failLogout = false
        var expectedLogoutRefreshToken = "social-refresh"
        var refreshedTokens = AuthTokens("social", "social-refresh")
        var onLogout: suspend () -> Unit = {}
        override suspend fun logout(refreshToken: String): Result<Unit> {
            logoutCalls++
            assertEquals(expectedLogoutRefreshToken, refreshToken)
            onLogout()
            return if (failLogout) Result.failure(IllegalStateException("response lost")) else Result.success(Unit)
        }
        override suspend fun loginAsGuest(): Result<AuthTokens> {
            guestCalls++
            return Result.success(AuthTokens("new-guest", "new-guest-refresh"))
        }
        override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
            refreshCalls++
            return Result.success(refreshedTokens)
        }
    }

    @Test fun withdrawalResumesAfterLostResponse() = runBlocking {
        val store = Store(); val gateway = Gateway().apply { failWithdrawal = true }
        val first = GuestSessionManager(gateway, store)
        try {
            assertTrue(first.withdraw().isFailure)
            assertEquals("LOGOUT:withdraw", store.marker)
            assertTrue(first.state.value.pendingWithdrawal)
        } finally { first.close() }
        val restored = GuestSessionManager(gateway, store)
        try {
            assertTrue(restored.state.value.pendingWithdrawal)
            gateway.failWithdrawal = false
            restored.withdraw().getOrThrow()
            assertEquals(2, gateway.withdrawalCalls)
            assertNull(store.tokens)
            assertNull(store.marker)
        } finally { restored.close() }
    }

    @Test fun logoutRevokesWithoutRefreshAndClearsBeforeNewGuest() = runBlocking {
        val store = Store(); val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        var cacheCleared = false
        session.onAccountChanged = { assertNull(store.tokens); cacheCleared = true }
        try {
            session.logout().getOrThrow()
            assertTrue(cacheCleared)
            assertNull(store.tokens)
            assertNull(store.marker)
            assertEquals(1, gateway.logoutCalls)
            assertEquals(0, gateway.refreshCalls)
            assertEquals(0, gateway.guestCalls)
            assertEquals(1, session.state.value.generation)
            assertEquals("logged_out", session.state.value.stage)
            session.ensureAuthenticated().getOrThrow()
            assertEquals(1, gateway.guestCalls)
            assertEquals("new-guest", store.tokens?.accessToken)
        } finally { session.close() }
    }

    @Test fun logoutCleanupCanRefreshWithoutDeadlockingAndRevokesRotatedToken() = runBlocking {
        val store = Store()
        val gateway = Gateway().apply {
            refreshedTokens = AuthTokens("rotated-access", "rotated-refresh")
            expectedLogoutRefreshToken = "rotated-refresh"
        }
        val session = GuestSessionManager(gateway, store)
        var sessionReadyCalls = 0
        session.onSessionReady = { sessionReadyCalls++ }
        session.onBeforeAccountChanged = { accessToken ->
            assertEquals("social", accessToken)
            withTimeout(1_000) { session.refreshAfterUnauthorized(checkNotNull(accessToken)).getOrThrow() }
            assertEquals("rotated-access", session.getAccessToken())
        }
        try {
            session.logout().getOrThrow()
            assertEquals(1, gateway.refreshCalls)
            assertEquals(1, gateway.logoutCalls)
            assertEquals(0, sessionReadyCalls)
            assertNull(store.tokens)
        } finally { session.close() }
    }

    @Test fun abortedLogoutRestoresRegistrationAfterCleanup() = runBlocking {
        for (failMarker in listOf(false, true)) {
            val store = Store()
            val gateway = Gateway()
            val session = GuestSessionManager(gateway, store)
            session.ensureAuthenticated().getOrThrow()
            var readyCalls = 0
            var canChange = true
            session.onSessionReady = {
                assertFalse(session.state.value.transitioning)
                assertFalse(session.state.value.pendingLogout)
                readyCalls++
            }
            session.canChangeAccount = { canChange }
            session.onBeforeAccountChanged = {
                if (failMarker) store.failMarker = true else canChange = false
            }
            try {
                val result = session.logout()
                assertTrue(result.isFailure)
                if (!failMarker) assertIs<AccountBusyException>(result.exceptionOrNull())
                assertEquals(1, readyCalls)
                assertEquals(0, gateway.logoutCalls)
                assertNotNull(store.tokens)
                assertNull(store.marker)
            } finally { session.close() }
        }
    }

    @Test fun lostRevokeResponseBlocksOldAccountAndResumesAfterRestart() = runBlocking {
        val store = Store(); val gateway = Gateway().apply { failLogout = true }
        val first = GuestSessionManager(gateway, store)
        try {
            assertTrue(first.logout().isFailure)
            assertEquals("LOGOUT", store.marker)
            assertTrue(first.state.value.pendingLogout)
            assertTrue(first.withAuthenticatedSession<Unit> { error("Old account must be blocked") }.isFailure)
            assertEquals(0, gateway.guestCalls)
        } finally { first.close() }
        val restored = GuestSessionManager(gateway, store)
        try {
            assertTrue(restored.state.value.pendingLogout)
            assertTrue(restored.restore().isFailure)
            assertEquals(0, gateway.refreshCalls)
            gateway.failLogout = false
            restored.logout().getOrThrow()
            assertEquals(2, gateway.logoutCalls)
            assertNull(store.tokens)
            assertNull(store.marker)
        } finally { restored.close() }
    }

    @Test fun storageDeletionRetryDoesNotRevokeOrRefreshAgain() = runBlocking {
        val store = Store().apply { failClear = true }; val gateway = Gateway()
        val session = GuestSessionManager(gateway, store)
        try {
            assertTrue(session.logout().isFailure)
            assertEquals("LOGOUT:revoked", store.marker)
            assertTrue(session.ensureAuthenticated().isFailure)
        } finally { session.close() }
        store.failClear = false
        val restored = GuestSessionManager(gateway, store)
        try {
            restored.logout().getOrThrow()
            assertEquals(1, gateway.logoutCalls)
            assertEquals(0, gateway.refreshCalls)
            assertEquals(0, gateway.guestCalls)
        } finally { restored.close() }
    }

    @Test fun cacheFailureAfterTokenDeletionKeepsRecoveryIntent() = runBlocking {
        val store = Store(); val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        session.onAccountChanged = { error("cache unavailable") }
        try {
            assertTrue(session.logout().isFailure)
            assertNull(store.tokens)
            assertEquals("LOGOUT:revoked", store.marker)
            assertTrue(session.ensureAuthenticated().isFailure)
            session.onAccountChanged = {}
            session.logout().getOrThrow()
            assertNull(store.marker)
            assertEquals(1, gateway.logoutCalls)
        } finally { session.close() }
    }

    @Test fun cannotLogoutWithQueuedSaveOrUnresolvedLogin() = runBlocking {
        val store = Store(); val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        try {
            session.canChangeAccount = { false }
            assertIs<AccountBusyException>(session.logout().exceptionOrNull())
            assertNull(store.marker)
            session.canChangeAccount = { true }
            store.marker = "GOOGLE:saved"
            assertIs<PendingAccountException>(session.logout().exceptionOrNull())
            assertEquals(0, gateway.logoutCalls)
        } finally { session.close() }
    }

    @Test fun concurrentLogoutSendsOneRevokeAndBlocksRefresh() = runBlocking {
        val store = Store(); val gateway = Gateway(); val session = GuestSessionManager(gateway, store)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        gateway.onLogout = { entered.complete(Unit); release.await() }
        try {
            val first = async { session.logout() }
            entered.await()
            assertIs<AccountTransitionException>(session.logout().exceptionOrNull())
            val refresh = async { session.refreshAfterUnauthorized("social") }
            release.complete(Unit)
            first.await().getOrThrow()
            assertTrue(refresh.await().isFailure)
            assertEquals(1, gateway.logoutCalls)
            assertEquals(0, gateway.refreshCalls)
        } finally { session.close() }
    }
}
