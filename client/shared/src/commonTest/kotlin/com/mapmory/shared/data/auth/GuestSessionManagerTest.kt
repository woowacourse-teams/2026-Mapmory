package com.mapmory.shared.data.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GuestSessionManagerTest {
    @Test
    fun invalidRefreshStopsPollingAndPreservesIdentity() = runBlocking {
        val error = com.mapmory.shared.data.remote.MapmoryApiException(401, "INVALID_REFRESH_TOKEN", null, null, null, emptyList())
        val gateway = FakeAuthGateway(refreshResult = Result.failure(error))
        val original = AuthTokens("old", "refresh")
        val store = FakeAuthTokenStore(original)
        var now = 0L
        val session = GuestSessionManager(gateway, store, elapsedMillis = { now })
        session.ensureAuthenticated()
        now = 1_000_000
        repeat(20) { session.ensureAuthenticated(); session.refreshAfterUnauthorized("old") }
        assertEquals(1, gateway.refreshCount)
        assertEquals(original, store.tokens)
        session.close()
    }

    @Test
    fun internalCancellationIsAnOrdinaryFailureForWaiters() = runBlocking {
        val gateway = object : AuthGateway {
            override suspend fun loginAsGuest(): Result<AuthTokens> = error("unused")
            override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
                throw kotlinx.coroutines.CancellationException("transport cancelled")
            }
        }
        val session = GuestSessionManager(gateway, FakeAuthTokenStore(AuthTokens("old", "refresh")))
        val error = session.ensureAuthenticated().exceptionOrNull()
        kotlin.test.assertTrue(error is IllegalStateException)
        kotlin.test.assertFalse(error is kotlinx.coroutines.CancellationException)
        session.close()
    }

    @Test
    fun transientFailureIsBoundedAndRecoversWithoutRestart() = runBlocking {
        var calls = 0
        var now = 0L
        var online = false
        val waits = mutableListOf<Long>()
        val gateway = object : AuthGateway {
            override suspend fun loginAsGuest(): Result<AuthTokens> = error("Must preserve identity")
            override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
                calls++
                return if (online) Result.success(AuthTokens("new", "new-refresh"))
                else Result.failure(com.mapmory.shared.data.remote.MapmoryConnectionException("offline", Exception()))
            }
        }
        val session = GuestSessionManager(gateway, FakeAuthTokenStore(AuthTokens("old", "refresh")),
            retryDelay = { waits += it }, elapsedMillis = { now })
        kotlin.test.assertTrue(session.ensureAuthenticated().isFailure)
        repeat(20) { session.ensureAuthenticated() }
        assertEquals(3, calls)
        assertEquals(listOf(1000L, 2000L), waits)
        online = true
        now = 60_000
        session.ensureAuthenticated().getOrThrow()
        assertEquals(4, calls)
        assertEquals("new", session.getAccessToken())
        session.close()
    }

    @Test
    fun cancellingCallerDoesNotCancelRotationOrOtherWaiters() = runBlocking {
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        val store = FakeAuthTokenStore(AuthTokens("old", "refresh"))
        val gateway = object : AuthGateway {
            override suspend fun loginAsGuest(): Result<AuthTokens> = error("Must preserve identity")
            override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
                calls++
                entered.complete(Unit)
                release.await()
                return Result.success(AuthTokens("new", "rotated"))
            }
        }
        val session = GuestSessionManager(gateway, store)
        val first = async { session.ensureAuthenticated() }
        entered.await()
        val second = async { session.ensureAuthenticated().getOrThrow() }
        first.cancel()
        release.complete(Unit)
        second.await()
        assertEquals(1, calls)
        assertEquals("rotated", store.tokens?.refreshToken)
        session.close()
    }

    @Test
    fun unauthorizedRotationWithFailedDiskWriteRetriesPersistenceNotTheConsumedRefreshToken() = runBlocking {
        var failWrites = false
        var saved: AuthTokens? = null
        val first = AuthTokens("access-1", "refresh-1")
        val rotated = AuthTokens("access-2", "refresh-2")
        val gateway = FakeAuthGateway(loginResult = Result.success(first), refreshResult = Result.success(rotated))
        val session = GuestSessionManager(gateway, object : AuthTokenStore {
            override fun load() = saved
            override fun save(tokens: AuthTokens) { check(!failWrites); saved = tokens }
            override fun clear() = Unit
        })
        session.ensureAuthenticated().getOrThrow()
        failWrites = true
        kotlin.test.assertTrue(session.refreshAfterUnauthorized("access-1").isFailure)
        assertEquals("access-2", session.getAccessToken())
        failWrites = false
        session.refreshAfterUnauthorized("access-1").getOrThrow()
        assertEquals(rotated, saved)
        assertEquals(1, gateway.refreshCount)
    }

    @Test
    fun temporaryStorageReadFailureNeverCreatesANewGuest() = runBlocking {
        val saved = AuthTokens("header.eyJleHAiOjIwMDB9.signature", "refresh")
        var unavailable = true
        val store = object : AuthTokenStore {
            override fun load(): AuthTokens? {
                if (unavailable) error("storage locked")
                return saved
            }
            override fun save(tokens: AuthTokens) = Unit
            override fun clear() = Unit
        }
        val gateway = FakeAuthGateway()
        val session = GuestSessionManager(gateway, store, nowEpochSeconds = { 1000 })
        kotlin.test.assertTrue(session.ensureAuthenticated().isFailure)
        assertEquals(0, gateway.loginCount)
        unavailable = false
        session.ensureAuthenticated().getOrThrow()
        assertEquals(saved.accessToken, session.getAccessToken())
        assertEquals(0, gateway.refreshCount)
    }

    @Test
    fun rotatedTokensAreRetainedAndPersistedWithoutRefreshingAgainAfterDiskFailure() = runBlocking {
        val old = AuthTokens("old", "old-refresh")
        val rotated = AuthTokens("new", "new-refresh")
        var unavailable = true
        var saved = old
        val store = object : AuthTokenStore {
            override fun load() = saved
            override fun save(tokens: AuthTokens) {
                if (unavailable) error("disk unavailable")
                saved = tokens
            }
            override fun clear() = Unit
        }
        val gateway = FakeAuthGateway(refreshResult = Result.success(rotated))
        val session = GuestSessionManager(gateway, store)
        kotlin.test.assertTrue(session.ensureAuthenticated().isFailure)
        assertEquals("new", session.getAccessToken())
        unavailable = false
        session.ensureAuthenticated().getOrThrow()
        assertEquals(rotated, saved)
        assertEquals(1, gateway.refreshCount)
        assertEquals(0, gateway.loginCount)
    }

    @Test
    fun missingTokensTriggerOnlyOneGuestLoginAcrossConcurrentRequests() = runBlocking {
        val issuedTokens = AuthTokens("guest-access", "guest-refresh")
        val gateway = FakeAuthGateway(loginResult = Result.success(issuedTokens))
        val store = FakeAuthTokenStore()
        val session = GuestSessionManager(gateway, store)

        coroutineScope {
            List(10) {
                async { session.ensureAuthenticated().getOrThrow() }
            }.awaitAll()
        }

        assertEquals(1, gateway.loginCount)
        assertEquals(0, gateway.refreshCount)
        assertEquals(issuedTokens, store.tokens)
        assertEquals("guest-access", session.getAccessToken())
    }

    @Test
    fun storedTokensAreRefreshedOnceAndRotatedAtAppSessionStart() = runBlocking {
        val storedTokens = AuthTokens("old-access", "old-refresh")
        val rotatedTokens = AuthTokens("new-access", "new-refresh")
        val gateway = FakeAuthGateway(refreshResult = Result.success(rotatedTokens))
        val store = FakeAuthTokenStore(storedTokens)
        val session = GuestSessionManager(gateway, store)

        coroutineScope {
            List(10) {
                async { session.ensureAuthenticated().getOrThrow() }
            }.awaitAll()
        }
        session.ensureAuthenticated().getOrThrow()

        assertEquals(0, gateway.loginCount)
        assertEquals(1, gateway.refreshCount)
        assertEquals(listOf("old-refresh"), gateway.refreshTokens)
        assertEquals(rotatedTokens, store.tokens)
        assertEquals("new-access", session.getAccessToken())
    }

    @Test
    fun validStoredAccessTokenSkipsRefreshAtAppSessionStart() = runBlocking {
        val storedTokens = AuthTokens(
            accessToken = "header.eyJleHAiOjIwMDB9.signature",
            refreshToken = "stored-refresh",
        )
        val gateway = FakeAuthGateway()
        val store = FakeAuthTokenStore(storedTokens)
        val session = GuestSessionManager(
            gateway = gateway,
            tokenStore = store,
            nowEpochSeconds = { 1_000L },
        )

        session.ensureAuthenticated().getOrThrow()

        assertEquals(0, gateway.loginCount)
        assertEquals(0, gateway.refreshCount)
        assertEquals(storedTokens.accessToken, session.getAccessToken())
    }

    @Test
    fun refreshFailureDoesNotCreateAnotherGuestOrDiscardStoredIdentity() = runBlocking {
        val storedTokens = AuthTokens("old-access", "old-refresh")
        val failure = IllegalStateException("refresh failed")
        val gateway = FakeAuthGateway(refreshResult = Result.failure(failure))
        val store = FakeAuthTokenStore(storedTokens)
        val session = GuestSessionManager(gateway, store)

        val result = session.ensureAuthenticated()

        assertSame(failure, result.exceptionOrNull())
        assertEquals(0, gateway.loginCount)
        assertEquals(1, gateway.refreshCount)
        assertEquals(storedTokens, store.tokens)
    }

    @Test
    fun refreshFailureIsReportedOnlyOncePerAppSession() = runBlocking {
        val storedTokens = AuthTokens("old-access", "old-refresh")
        val failure = IllegalStateException("refresh failed")
        val reportedFailures = mutableListOf<Pair<String, Throwable>>()
        val session = GuestSessionManager(
            gateway = FakeAuthGateway(refreshResult = Result.failure(failure)),
            tokenStore = FakeAuthTokenStore(storedTokens),
            onRefreshFailed = { stage, error -> reportedFailures += stage to error },
        )

        session.ensureAuthenticated()
        session.ensureAuthenticated()

        assertEquals(1, reportedFailures.size)
        assertEquals("startup", reportedFailures.single().first)
        assertSame(failure, reportedFailures.single().second)
    }

    @Test
    fun unauthorizedConcurrentRequestsRotateRefreshTokenOnlyOnce() = runBlocking {
        val firstTokens = AuthTokens("expired-access", "first-refresh")
        val rotatedTokens = AuthTokens("rotated-access", "rotated-refresh")
        val gateway = FakeAuthGateway(
            loginResult = Result.success(firstTokens),
            refreshResult = Result.success(rotatedTokens),
        )
        val store = FakeAuthTokenStore()
        val session = GuestSessionManager(gateway, store)
        session.ensureAuthenticated().getOrThrow()

        coroutineScope {
            List(10) {
                async {
                    session.refreshAfterUnauthorized("expired-access").getOrThrow()
                }
            }.awaitAll()
        }

        assertEquals(1, gateway.loginCount)
        assertEquals(1, gateway.refreshCount)
        assertEquals(listOf("first-refresh"), gateway.refreshTokens)
        assertEquals(rotatedTokens, store.tokens)
        assertEquals("rotated-access", session.getAccessToken())
    }

    @Test
    fun requestThatFailedWithPreviousTokenUsesAlreadyRotatedSession() = runBlocking {
        val firstTokens = AuthTokens("expired-access", "first-refresh")
        val rotatedTokens = AuthTokens("rotated-access", "rotated-refresh")
        val gateway = FakeAuthGateway(
            loginResult = Result.success(firstTokens),
            refreshResult = Result.success(rotatedTokens),
        )
        val session = GuestSessionManager(gateway, FakeAuthTokenStore())
        session.ensureAuthenticated().getOrThrow()
        session.refreshAfterUnauthorized("expired-access").getOrThrow()

        session.refreshAfterUnauthorized("expired-access").getOrThrow()

        assertEquals(1, gateway.refreshCount)
        assertEquals("rotated-access", session.getAccessToken())
    }
}

private class FakeAuthGateway(
    private val loginResult: Result<AuthTokens> = Result.failure(UnsupportedOperationException()),
    private val refreshResult: Result<AuthTokens> = Result.failure(UnsupportedOperationException()),
) : AuthGateway {
    var loginCount: Int = 0
        private set
    var refreshCount: Int = 0
        private set
    val refreshTokens = mutableListOf<String>()

    override suspend fun loginAsGuest(): Result<AuthTokens> {
        loginCount += 1
        yield()
        return loginResult
    }

    override suspend fun refresh(refreshToken: String): Result<AuthTokens> {
        refreshCount += 1
        refreshTokens += refreshToken
        yield()
        return refreshResult
    }
}

private class FakeAuthTokenStore(
    initialTokens: AuthTokens? = null,
) : AuthTokenStore {
    var tokens: AuthTokens? = initialTokens
        private set

    override fun load(): AuthTokens? = tokens

    override fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}
