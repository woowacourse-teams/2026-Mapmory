package com.mapmory.shared.data.auth

import com.mapmory.shared.data.remote.MapmoryApiException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class WithdrawalRecoveryTest {
    private fun apiError(code: String) = MapmoryApiException(401, code, null, null, null, emptyList())

    private class Store : AuthTokenStore {
        var saved: AuthTokens? = AuthTokens("old-access", "old-refresh")
        var marker: String? = null
        var failClear = false
        override fun load() = saved
        override fun save(tokens: AuthTokens) { saved = tokens }
        override fun clear() { check(!failClear); saved = null }
        override fun pendingProvider() = AuthPendingRead(marker)
        override fun markPendingProvider(provider: String?) { marker = provider }
    }

    private inner class Gateway : AuthGateway {
        var expired = false
        var lostResponse = true
        var deleted = false
        var refreshError: Throwable? = null
        var refreshCalls = 0
        val requests = mutableListOf<String>()
        override suspend fun loginAsGuest() = Result.success(AuthTokens("guest", "guest-refresh"))
        override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
            refreshCalls++
            refreshError?.let { return Result.failure(it) }
            return Result.success(if (expired) AuthTokens("fresh-access", "fresh-refresh") else AuthTokens("old-access", "old-refresh"))
        }
        override suspend fun withdraw(accessToken: String, identityToken: String?, authorizationCode: String?): Result<Unit> {
            requests += accessToken
            if (expired && accessToken == "old-access") return Result.failure(apiError("EXPIRED_ACCESS_TOKEN"))
            if (lostResponse) return Result.failure(IllegalStateException("network failure"))
            deleted = true
            return Result.success(Unit)
        }
    }

    private suspend fun startFailedWithdrawal(store: Store, gateway: Gateway) {
        val first = GuestSessionManager(gateway, store)
        try { assertTrue(first.withdraw().isFailure) } finally { first.close() }
        assertEquals("LOGOUT:withdraw", store.marker)
        // Time passes and the app is restarted with the persisted token and intent.
        gateway.expired = true
    }

    @Test fun requestNeverArrivedRefreshesAndRetriesAfterRestart() = runBlocking {
        val store = Store(); val gateway = Gateway()
        startFailedWithdrawal(store, gateway)
        gateway.lostResponse = false
        val restarted = GuestSessionManager(gateway, store)
        val before = gateway.refreshCalls
        try {
            restarted.withdraw().getOrThrow()
            assertEquals(listOf("old-access", "fresh-access"), gateway.requests.takeLast(2))
            assertEquals(before + 1, gateway.refreshCalls)
            assertTrue(gateway.deleted)
            assertNull(store.marker)
            assertNull(store.saved)
            assertFalse(restarted.state.value.pendingLogout)
        } finally { restarted.close() }
    }

    @Test fun lostSuccessAndRevokedRefreshReleasesLockWithoutClaimingDeletion() = runBlocking {
        val store = Store(); val gateway = Gateway()
        startFailedWithdrawal(store, gateway)
        gateway.deleted = true // Server committed before the response was lost.
        gateway.refreshError = apiError("INVALID_REFRESH_TOKEN")
        val restarted = GuestSessionManager(gateway, store)
        try {
            assertIs<WithdrawalUnconfirmedException>(restarted.withdraw().exceptionOrNull())
            assertFalse(restarted.state.value.pendingLogout)
            assertTrue(restarted.state.value.withdrawalUnconfirmed)
            assertNull(store.marker)
            assertNull(store.saved)
            restarted.ensureAuthenticated().getOrThrow()
            assertEquals("guest", restarted.getAccessToken())
        } finally { restarted.close() }
    }

    @Test fun refreshNetworkFailureRetainsIntentAndCanRetry() = runBlocking {
        val store = Store(); val gateway = Gateway()
        startFailedWithdrawal(store, gateway)
        gateway.refreshError = IllegalStateException("offline")
        val restarted = GuestSessionManager(gateway, store)
        try {
            assertTrue(restarted.withdraw().isFailure)
            assertEquals("LOGOUT:withdraw", store.marker)
            assertTrue(restarted.state.value.pendingLogout)
            assertFalse(restarted.state.value.withdrawalUnconfirmed)
            gateway.refreshError = null
            gateway.lostResponse = false
            restarted.withdraw().getOrThrow()
            assertNull(store.marker)
        } finally { restarted.close() }
    }

    @Test fun unconfirmedOutcomeSurvivesCleanupFailureAndRestart() = runBlocking {
        val store = Store(); val gateway = Gateway()
        startFailedWithdrawal(store, gateway)
        gateway.refreshError = apiError("EXPIRED_REFRESH_TOKEN")
        store.failClear = true
        val restarted = GuestSessionManager(gateway, store)
        try {
            assertTrue(restarted.withdraw().isFailure)
            assertEquals("LOGOUT:withdraw:unconfirmed", store.marker)
        } finally { restarted.close() }
        store.failClear = false
        val cleanup = GuestSessionManager(gateway, store)
        val before = gateway.requests.size
        try {
            assertIs<WithdrawalUnconfirmedException>(cleanup.withdraw().exceptionOrNull())
            assertEquals(before, gateway.requests.size)
            assertTrue(cleanup.state.value.withdrawalUnconfirmed)
            assertFalse(cleanup.state.value.pendingLogout)
        } finally { cleanup.close() }
    }
}
