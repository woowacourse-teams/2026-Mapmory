package com.mapmory.shared.app

import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.remote.configureCommonHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.save
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class HttpClientConfigurationTest {
    @Test
    fun pauseBeforeSendLeavesTheActualTransportTimeoutIntact() = runBlocking<Unit> {
        var sent = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    sent++
                    if (request.url.encodedPath == "/slow") delay(500)
                    respond("ok", HttpStatusCode.OK)
                }
            }
        }
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            if (context.url.encodedPath == "/paused") {
                delay(500)
                assertEquals(0, sent)
            }
        }
        try {
            assertEquals(HttpStatusCode.OK, client.get("https://example.com/paused") {
                timeout { requestTimeoutMillis = 100 }
            }.status)
            assertEquals(1, sent)
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("https://example.com/slow") { timeout { requestTimeoutMillis = 100 } }
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun observerSeesExpiredResponseRefreshAndReplayOnTheSameClient() = runBlocking {
        var reads = 0
        val seen = mutableListOf<Int>()
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    val body: String
                    val status: HttpStatusCode
                    when {
                        request.url.encodedPath.endsWith("/login/guest") || request.url.encodedPath.endsWith("/token/refresh") -> {
                            status = HttpStatusCode.OK
                            body = """{"data":{"accessToken":"access-${reads}","refreshToken":"refresh","isNewMember":true}}"""
                        }
                        reads++ == 0 -> {
                            status = HttpStatusCode.Unauthorized
                            body = """{"code":"EXPIRED_ACCESS_TOKEN"}"""
                        }
                        else -> {
                            status = HttpStatusCode.OK
                            body = """{"data":{"nickname":"test","provider":"GUEST"}}"""
                        }
                    }
                    respond(body, status, headersOf("Content-Type", "application/json"))
                }
            }
        }
        val container = createGuestRemoteAppContainer(
            client = client,
            apiBaseUrl = "https://example.com/api/v1",
            tokenStore = object : AuthTokenStore {
                override fun load(): AuthTokens? = null
                override fun save(tokens: AuthTokens) = Unit
                override fun clear() = Unit
            },
            configureHttpClient = { configured ->
                assertSame(client, configured)
                configured.requestPipeline.intercept(HttpRequestPipeline.Before) {
                    val refreshing = context.url.encodedPath.endsWith("/token/refresh")
                    context.timeout { requestTimeoutMillis = if (refreshing) 100 else 1_000 }
                    if (refreshing) delay(500)
                }
                configured.plugin(HttpSend).intercept { request ->
                    val call = execute(request)
                    seen += call.response.status.value
                    if (call.response.status == HttpStatusCode.Unauthorized) {
                        call.save().also { assertTrue(it.response.bodyAsText().contains("EXPIRED_ACCESS_TOKEN")) }
                    } else call
                }
            },
        )
        try {
            assertTrue(checkNotNull(container.accountSession).restore().isSuccess)
            assertEquals(listOf(200, 401, 200, 200), seen)
        } finally {
            container.close()
        }
    }

    @Test
    fun observerPreservesThreeExpiredRequestsAndSingleRefresh() = runBlocking {
        withTimeout(10_000) {
            val mutex = Mutex()
            val allExpiredRequestsArrived = CompletableDeferred<Unit>()
            var expired = false
            var expiredRequests = 0
            var refreshes = 0
            var observedExpired = 0
            val client = HttpClient(MockEngine) {
                configureCommonHttpClient()
                engine { addHandler { request ->
                    var status = HttpStatusCode.OK
                    val body = when {
                        request.url.encodedPath.endsWith("/login/guest") ->
                            """{"data":{"accessToken":"initial","refreshToken":"refresh","isNewMember":true}}"""
                        request.url.encodedPath.endsWith("/token/refresh") -> {
                            mutex.withLock { refreshes++ }
                            """{"data":{"accessToken":"rotated","refreshToken":"rotated-refresh"}}"""
                        }
                        expired && request.headers[HttpHeaders.Authorization] == "Bearer initial" -> {
                            mutex.withLock { if (++expiredRequests == 3) allExpiredRequestsArrived.complete(Unit) }
                            allExpiredRequestsArrived.await()
                            status = HttpStatusCode.Unauthorized
                            """{"code":"EXPIRED_ACCESS_TOKEN"}"""
                        }
                        else -> """{"data":{"nickname":"test","provider":"GUEST"}}"""
                    }
                    respond(body, status, headersOf("Content-Type", "application/json"))
                } }
            }
            val container = createGuestRemoteAppContainer(
                client = client,
                apiBaseUrl = "https://example.com/api/v1",
                tokenStore = object : AuthTokenStore {
                    override fun load(): AuthTokens? = null
                    override fun save(tokens: AuthTokens) = Unit
                    override fun clear() = Unit
                },
                configureHttpClient = { configured ->
                    configured.plugin(HttpSend).intercept { request ->
                        val call = execute(request)
                        if (call.response.status == HttpStatusCode.Unauthorized) {
                            mutex.withLock { observedExpired++ }
                            call.save().also { it.response.bodyAsText() }
                        } else call
                    }
                },
            )
            try {
                checkNotNull(container.accountSession).restore().getOrThrow()
                expired = true
                val statuses = coroutineScope {
                    List(3) { async {
                        client.get("https://example.com/api/v1/members/me") { bearerAuth("initial") }.status
                    } }.awaitAll()
                }
                assertEquals(List(3) { HttpStatusCode.OK }, statuses)
                assertEquals(3, expiredRequests)
                assertEquals(3, observedExpired)
                assertEquals(1, refreshes)
            } finally {
                container.close()
            }
        }
    }
}
