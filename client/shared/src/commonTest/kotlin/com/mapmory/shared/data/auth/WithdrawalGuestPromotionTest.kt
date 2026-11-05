package com.mapmory.shared.data.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class WithdrawalGuestPromotionTest {
    @Test fun withdrawalClearsOldTokensAndAppleLoginUsesNewGuestSession() = runBlocking {
        var saved: AuthTokens? = AuthTokens("old-apple", "old-refresh")
        var marker: String? = null
        var guestCalls = 0
        var uploadedWith: String? = null
        var loginWith: String? = null
        val store = object : AuthTokenStore {
            override fun load() = saved
            override fun save(tokens: AuthTokens) { saved = tokens }
            override fun clear() { saved = null }
            override fun pendingProvider() = AuthPendingRead(marker)
            override fun markPendingProvider(provider: String?) { marker = provider }
        }
        val gateway = object : AuthGateway {
            override suspend fun refresh(refreshToken: String) = Result.success(AuthTokens("old-apple", "old-refresh"))
            override suspend fun withdraw(accessToken: String, identityToken: String?, authorizationCode: String?): Result<Unit> {
                assertEquals("old-apple", accessToken)
                return Result.success(Unit)
            }
            override suspend fun loginAsGuest(): Result<AuthTokens> {
                guestCalls++
                return Result.success(AuthTokens("new-guest", "new-guest-refresh"))
            }
            override suspend fun socialLogin(provider: MemberProvider, idToken: String, accessToken: String?): Result<SocialSessionTokens> {
                assertEquals(MemberProvider.APPLE, provider)
                loginWith = accessToken
                return Result.success(SocialSessionTokens(AuthTokens("promoted-apple", "promoted-refresh"), false))
            }
            override suspend fun member(accessToken: String): Result<AccountMember> {
                assertEquals("promoted-apple", accessToken)
                return Result.success(AccountMember("tester", MemberProvider.APPLE))
            }
        }
        val session = GuestSessionManager(gateway, store)
        try {
            session.withdraw("proof", "code").getOrThrow()
            assertNull(saved)
            assertNull(session.getAccessToken())
            assertEquals(0, guestCalls)
            session.withAuthenticatedSession {
                uploadedWith = session.getAccessToken()
                Result.success(Unit)
            }.getOrThrow()
            assertEquals("new-guest", uploadedWith)
            assertEquals("new-guest-refresh", saved?.refreshToken)
            session.login(MemberProvider.APPLE, "new-proof").getOrThrow()
            assertEquals(uploadedWith, loginWith)
            assertEquals(1, guestCalls)
            assertEquals("promoted-apple", session.getAccessToken())
            assertEquals("promoted-refresh", saved?.refreshToken)
            assertFalse(session.state.value.guestAbandoned)
        } finally { session.close() }
    }
}
