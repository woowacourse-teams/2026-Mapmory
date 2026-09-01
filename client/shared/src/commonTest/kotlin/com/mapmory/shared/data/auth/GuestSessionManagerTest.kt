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
