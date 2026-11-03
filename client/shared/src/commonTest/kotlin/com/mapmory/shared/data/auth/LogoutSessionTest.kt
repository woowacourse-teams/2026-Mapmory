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
        var logoutCalls = 0
        var guestCalls = 0
        var refreshCalls = 0
        var failLogout = false
        var onLogout: suspend () -> Unit = {}
        override suspend fun logout(refreshToken: String): Result<Unit> {
            logoutCalls++
            assertEquals("social-refresh", refreshToken)
            onLogout()
            return if (failLogout) Result.failure(IllegalStateException("response lost")) else Result.success(Unit)
        }
        override suspend fun loginAsGuest(): Result<AuthTokens> {
            guestCalls++
            return Result.success(AuthTokens("new-guest", "new-guest-refresh"))
        }
        override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
            refreshCalls++
            return Result.success(AuthTokens("social", "social-refresh"))
        }
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
