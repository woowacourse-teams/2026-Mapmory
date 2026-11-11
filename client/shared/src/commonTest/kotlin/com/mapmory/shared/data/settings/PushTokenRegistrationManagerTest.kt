package com.mapmory.shared.data.settings

import com.mapmory.shared.data.remote.AccessTokenProvider
import com.mapmory.shared.data.remote.configureCommonHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushTokenRegistrationManagerTest {
    @Test
    fun tokenRotationAndFeatureChangeKeepTheSameDeviceRegistration() = runBlocking {
        val store = MemoryPushTokenRegistrationStore()
        val token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ."
        var puts = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine { addHandler { request ->
                assertEquals("PUT", request.method.value)
                puts++
                val body = assertIs<TextContent>(request.body).text
                if (puts > 1) assertTrue(body.contains("\"previousPushTokenId\":41"))
                if (puts == 2) {
                    assertTrue(body.contains("\"token\":\"rotated\""))
                    assertTrue(body.contains("\"travelVideoCompletionEnabled\":false"))
                }
                respond("""{"data":{"pushTokenId":41}}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            } }
        }
        val manager = PushTokenRegistrationManager(client, "https://api.example.com/api/v1",
            AccessTokenProvider { token }, store, "ANDROID")
        try {
            manager.updateDeviceState("initial", true, photoEnabled = true, videoEnabled = true)
            assertEquals(41L, manager.videoPushTokenId())
            manager.updateDeviceState("rotated", true, photoEnabled = true, videoEnabled = false)
            assertEquals(41L, manager.photoPushTokenId())
            assertNull(manager.videoPushTokenId())
            assertEquals(2, puts)
            assertEquals(PushTokenRegistration("account-1", 41), store.read())
            manager.updateDeviceState("rotated", true, photoEnabled = false, videoEnabled = true)
            assertEquals(41L, manager.videoPushTokenId())
            assertEquals(3, puts)
        } finally { manager.close(); client.close() }
    }

    @Test
    fun inFlightTokenRotationPreservesExistingRegistration() = runBlocking {
        val token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ."
        val store = MemoryPushTokenRegistrationStore().apply {
            write(PushTokenRegistration("account-1", 41))
        }
        val registrationStarted = CompletableDeferred<Unit>()
        val finishRegistration = CompletableDeferred<Unit>()
        var puts = 0
        var deletes = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine { addHandler { request ->
                if (request.method.value == "DELETE") {
                    deletes++
                    respond("", HttpStatusCode.NoContent)
                } else {
                    puts++
                    assertTrue(assertIs<TextContent>(request.body).text.contains("\"previousPushTokenId\":41"))
                    if (puts == 1) {
                        registrationStarted.complete(Unit)
                        finishRegistration.await()
                    }
                    respond("""{"data":{"pushTokenId":41}}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            } }
        }
        val manager = PushTokenRegistrationManager(client, "https://api.example.com/api/v1",
            AccessTokenProvider { token }, store, "ANDROID")
        try {
            manager.updateDeviceState("initial", true, photoEnabled = true, videoEnabled = true)
            registrationStarted.await()
            manager.updateDeviceState("rotated", true, photoEnabled = true, videoEnabled = true)
            finishRegistration.complete(Unit)
            assertEquals(41L, manager.photoPushTokenId())
            assertEquals(PushTokenRegistration("account-1", 41), store.read())
            assertEquals(2, puts)
            assertEquals(0, deletes)
        } finally { manager.close(); client.close() }
    }

    @Test
    fun failedDisableCleanupRetainsRegistrationForRetry() = runBlocking {
        val store = MemoryPushTokenRegistrationStore().apply {
            write(PushTokenRegistration("account-1", 41))
        }
        var allowDelete = false
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine { addHandler {
                if (allowDelete) respond("", HttpStatusCode.NoContent)
                else respond("", HttpStatusCode.ServiceUnavailable)
            } }
        }
        val manager = PushTokenRegistrationManager(client, "https://api.example.com/api/v1",
            AccessTokenProvider { "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ." }, store, "ANDROID")
        try {
            manager.updateDeviceState("device-token", true, photoEnabled = false, videoEnabled = false)
            assertNull(manager.photoPushTokenId())
            assertEquals(PushTokenRegistration("account-1", 41), store.read())
            allowDelete = true
            assertNull(manager.photoPushTokenId())
            assertNull(store.read())
        } finally { manager.close(); client.close() }
    }

    @Test
    fun failedAccountCleanupRetainsRegistrationAndBlocksReregistration() = runBlocking {
        val store = MemoryPushTokenRegistrationStore().apply {
            write(PushTokenRegistration("account-1", 41))
        }
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine { addHandler { respond("", HttpStatusCode.ServiceUnavailable) } }
        }
        val token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ."
        val manager = PushTokenRegistrationManager(client, "https://api.example.com/api/v1",
            AccessTokenProvider { token }, store, "IOS")
        try {
            manager.beforeAccountChanged(token)
            assertEquals(PushTokenRegistration("account-1", 41), store.read())
            manager.updateDeviceState("device-token", true, photoEnabled = true, videoEnabled = true)
            assertNull(manager.videoPushTokenId())
        } finally { manager.close(); client.close() }
    }

    @Test
    fun unknownDeviceStateDoesNotDeletePersistedRegistration() = runBlocking {
        val store = MemoryPushTokenRegistrationStore().apply {
            write(PushTokenRegistration("account-1", 41))
        }
        var requests = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine { addHandler { requests++; error("Device state is unknown") } }
        }
        val manager = PushTokenRegistrationManager(client, "https://api.example.com/api/v1",
            AccessTokenProvider { "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ." }, store, "ANDROID")
        try {
            assertNull(manager.photoPushTokenId())
            assertNull(manager.videoPushTokenId())
            assertEquals(0, requests)
            assertEquals(PushTokenRegistration("account-1", 41), store.read())
        } finally { manager.close(); client.close() }
    }

    @Test
    fun registrationIsSharedByFeaturesAndRemovedWhenBothAreDisabled() = runBlocking {
        val token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ."
        val store = MemoryPushTokenRegistrationStore()
        var puts = 0
        var deletes = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
                    assertEquals("/api/v1/push-tokens", request.url.encodedPath)
                    when (request.method.value) {
                        "PUT" -> {
                            puts++
                            val body = assertIs<TextContent>(request.body).text
                            assertTrue(body.contains("\"token\":\"device-token\""))
                            assertTrue(body.contains("\"platform\":\"ANDROID\""))
                            respond(
                                ByteReadChannel("""{"data":{"pushTokenId":41}}"""),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        "DELETE" -> {
                            deletes++
                            assertEquals("""{"pushTokenId":41}""", assertIs<TextContent>(request.body).text)
                            respond("", HttpStatusCode.NoContent)
                        }
                        else -> error("Unexpected request: ${request.method}")
                    }
                }
            }
        }
        val manager = PushTokenRegistrationManager(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { token },
            store = store,
            platform = "ANDROID",
        )

        try {
            manager.updateDeviceState("device-token", true, photoEnabled = true, videoEnabled = true)
            assertEquals(41L, manager.photoPushTokenId())
            assertEquals(41L, manager.videoPushTokenId())
            assertEquals(1, puts)
            assertEquals(PushTokenRegistration("account-1", 41), store.read())

            manager.updateDeviceState("device-token", true, photoEnabled = false, videoEnabled = false)
            assertNull(manager.photoPushTokenId())
            assertEquals(1, deletes)
            assertNull(store.read())
        } finally {
            manager.close()
            client.close()
        }
    }

    @Test
    fun accountSwitchDeletesOnlyThePreviousAccountsRegistration() = runBlocking {
        val previousToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ."
        val nextToken = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTIifQ."
        var currentAccessToken = previousToken
        val store = MemoryPushTokenRegistrationStore().apply {
            write(PushTokenRegistration("account-1", 41))
        }
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    when (request.method.value) {
                        "DELETE" -> {
                            assertEquals("Bearer $previousToken", request.headers[HttpHeaders.Authorization])
                            assertEquals("""{"pushTokenId":41}""", assertIs<TextContent>(request.body).text)
                            respond("", HttpStatusCode.NoContent)
                        }
                        "PUT" -> {
                            assertEquals("Bearer $nextToken", request.headers[HttpHeaders.Authorization])
                            assertTrue(assertIs<TextContent>(request.body).text.contains("\"token\":\"device-token\""))
                            respond(
                                ByteReadChannel("""{"data":{"pushTokenId":99}}"""),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        else -> error("Unexpected request: ${request.method}")
                    }
                }
            }
        }
        val manager = PushTokenRegistrationManager(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { currentAccessToken },
            store = store,
            platform = "IOS",
        )

        try {
            manager.beforeAccountChanged(previousToken)
            assertNull(store.read())
            manager.updateDeviceState("device-token", true, photoEnabled = true, videoEnabled = true)
            currentAccessToken = nextToken
            manager.onSessionReady()
            assertEquals(99L, manager.videoPushTokenId())
            assertEquals(PushTokenRegistration("account-2", 99), store.read())
        } finally {
            manager.close()
            client.close()
        }
    }

    @Test
    fun inFlightRegistrationIsDeletedInsteadOfPersistedWhenAccountSwitchStarts() = runBlocking {
        val token = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhY2NvdW50LTEifQ."
        val registrationStarted = CompletableDeferred<Unit>()
        val finishRegistration = CompletableDeferred<Unit>()
        var deletes = 0
        val store = MemoryPushTokenRegistrationStore()
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    when (request.method.value) {
                        "PUT" -> {
                            registrationStarted.complete(Unit)
                            finishRegistration.await()
                            respond(
                                ByteReadChannel("""{"data":{"pushTokenId":41}}"""),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        "DELETE" -> {
                            deletes++
                            assertEquals("""{"pushTokenId":41}""", assertIs<TextContent>(request.body).text)
                            respond("", HttpStatusCode.NoContent)
                        }
                        else -> error("Unexpected request: ${request.method}")
                    }
                }
            }
        }
        val manager = PushTokenRegistrationManager(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { token },
            store = store,
            platform = "ANDROID",
        )

        try {
            manager.updateDeviceState("device-token", true, photoEnabled = true, videoEnabled = true)
            registrationStarted.await()
            val accountSwitch = async(start = CoroutineStart.UNDISPATCHED) {
                manager.beforeAccountChanged(token)
            }
            manager.updateDeviceState("rotated-token", true, photoEnabled = true, videoEnabled = true)
            finishRegistration.complete(Unit)
            accountSwitch.await()
            assertNull(manager.photoPushTokenId())

            assertNull(store.read())
            assertEquals(1, deletes)
        } finally {
            manager.close()
            client.close()
        }
    }
}

private class MemoryPushTokenRegistrationStore : PushTokenRegistrationStore {
    private var registration: PushTokenRegistration? = null
    override fun read() = registration
    override fun write(registration: PushTokenRegistration) { this.registration = registration }
    override fun clear() { registration = null }
}
