package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SocialLoginContractTest {
    @Test fun logoutSendsRefreshWithoutBearerAndAcceptsNoContent() = runBlocking {
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine { addHandler { request ->
                assertEquals("/api/v1/auth/logout", request.url.encodedPath)
                assertEquals(HttpMethod.Post, request.method)
                assertNull(request.headers[HttpHeaders.Authorization])
                assertEquals("""{"refreshToken":"session-refresh"}""", request.body.toByteArray().decodeToString())
                respond("", HttpStatusCode.NoContent)
            } }
        }
        try {
            AuthRemoteRepository(client, "https://example.com/api/v1").logout("session-refresh").getOrThrow()
        } finally { client.close() }
    }
    @Test fun expiredGuestHeaderRefreshesOnceAndReplaysOnlySocialHttpRequest() = runBlocking {
        var exchanges = 0
        var refreshes = 0
        var saved: AuthTokens? = null
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine { addHandler { request ->
                val body: String
                var status = HttpStatusCode.OK
                when (request.url.encodedPath) {
                    "/api/v1/auth/login/guest" -> body = """{"data":{"accessToken":"guest","refreshToken":"guest-r","isNewMember":true}}"""
                    "/api/v1/auth/token/refresh" -> {
                        refreshes++
                        body = """{"data":{"accessToken":"fresh-guest","refreshToken":"fresh-r"}}"""
                    }
                    "/api/v1/auth/login/google" -> {
                        exchanges++
                        assertEquals("""{"idToken":"proof"}""", request.body.toByteArray().decodeToString())
                        if (exchanges == 1) {
                            assertEquals("Bearer guest", request.headers[HttpHeaders.Authorization])
                            status = HttpStatusCode.Unauthorized
                            body = """{"status":401,"code":"EXPIRED_ACCESS_TOKEN"}"""
                        } else {
                            assertEquals("Bearer fresh-guest", request.headers[HttpHeaders.Authorization])
                            body = """{"data":{"accessToken":"social","refreshToken":"social-r","guestAbandoned":false}}"""
                        }
                    }
                    "/api/v1/members/me" -> {
                        assertEquals("Bearer social", request.headers[HttpHeaders.Authorization])
                        body = """{"data":{"nickname":"닉네임","provider":"GOOGLE"}}"""
                    }
                    else -> error("Unexpected request")
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            } }
        }
        val store = object : AuthTokenStore {
            override fun load() = saved
            override fun save(tokens: AuthTokens) { saved = tokens }
            override fun clear() = error("Must not discard session")
        }
        val session = GuestSessionManager(AuthRemoteRepository(client, "https://example.com/api/v1"), store)
        client.installMapmoryAuthRetry(session, "https://example.com/api/v1")
        try {
            session.login(MemberProvider.GOOGLE, "proof").getOrThrow()
            assertEquals(2, exchanges)
            assertEquals(1, refreshes)
            assertEquals("social-r", saved?.refreshToken)
        } finally { session.close(); client.close() }
    }

    @Test fun eachProviderUsesBackendDevelopTokenContractAndGuestBearer() = runBlocking {
        for (provider in listOf(MemberProvider.GOOGLE, MemberProvider.KAKAO, MemberProvider.APPLE)) {
            val client = HttpClient(MockEngine) {
                configureCommonHttpClient()
                engine { addHandler { request ->
                    assertEquals("/api/v1/auth/login/${provider.name.lowercase()}", request.url.encodedPath)
                    assertEquals("Bearer guest", request.headers[HttpHeaders.Authorization])
                    val field = if (provider == MemberProvider.APPLE) "identityToken" else "idToken"
                    assertEquals("""{"$field":"proof"}""", request.body.toByteArray().decodeToString())
                    respond("""{"data":{"accessToken":"new","refreshToken":"new-r","isNewMember":false,"guestAbandoned":true}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } }
            }
            try {
                val result = AuthRemoteRepository(client, "https://example.com/api/v1").socialLogin(provider, "proof", "guest").getOrThrow()
                assertTrue(result.guestAbandoned)
                assertEquals("new", result.tokens.accessToken)
            } finally { client.close() }
        }
    }
}
