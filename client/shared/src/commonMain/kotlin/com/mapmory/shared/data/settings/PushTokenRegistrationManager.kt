package com.mapmory.shared.data.settings

import com.mapmory.shared.data.auth.jwtSubjectOrNull
import com.mapmory.shared.data.remote.AccessTokenProvider
import com.mapmory.shared.data.remote.requireSuccess
import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.PushTokenDeleteRequestDto
import com.mapmory.shared.data.remote.model.PushTokenRegistrationDto
import com.mapmory.shared.data.remote.model.PushTokenRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.bearerAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class PushTokenRegistration(
    val accountIdentity: String,
    val pushTokenId: Long,
)

interface PushTokenRegistrationStore {
    fun read(): PushTokenRegistration?
    fun write(registration: PushTokenRegistration)
    fun clear()
}

/** Keeps the FCM token in memory and persists only its server-side registration id. */
class PushTokenRegistrationManager(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
    private val store: PushTokenRegistrationStore,
    private val platform: String,
) {
    private val endpoint = "${apiBaseUrl.trimEnd('/')}/push-tokens"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val deviceState = MutableStateFlow(DeviceState())
    private var registeredToken: String? = null
    private var registeredAccount: String? = null
    private var registeredPreferences: Pair<Boolean, Boolean>? = null

    init {
        require(platform == "ANDROID" || platform == "IOS")
    }

    fun updateDeviceState(
        token: String?,
        notificationPermissionGranted: Boolean,
        photoEnabled: Boolean,
        videoEnabled: Boolean,
    ) {
        deviceState.update { it.copy(
            token = token?.takeIf(String::isNotBlank),
            notificationPermissionGranted = notificationPermissionGranted,
            photoEnabled = photoEnabled,
            videoEnabled = videoEnabled,
            deviceStateKnown = true,
        ) }
        scope.launch { runCatchingSync() }
    }

    fun onSessionReady() {
        deviceState.update { it.copy(accountChanging = false) }
        if (deviceState.value.deviceStateKnown) scope.launch { runCatchingSync() }
    }

    suspend fun photoPushTokenId(): Long? = tokenIdFor(photo = true)

    suspend fun videoPushTokenId(): Long? = tokenIdFor(photo = false)

    suspend fun refreshPhotoPushTokenId(rejectedId: Long): Long? = refreshRejectedTokenId(rejectedId, photo = true)

    suspend fun refreshVideoPushTokenId(rejectedId: Long): Long? = refreshRejectedTokenId(rejectedId, photo = false)

    suspend fun beforeAccountChanged(oldAccessToken: String?) {
        deviceState.update { it.copy(accountChanging = true) }
        withContext(Dispatchers.Default) {
            withTimeoutOrNull(AccountSwitchCleanupTimeoutMillis) {
                mutex.withLock {
                    val registration = runCatching { store.read() }.getOrNull()
                    val oldIdentity = oldAccessToken?.jwtSubjectOrNull()
                    if (registration != null && oldIdentity != null && registration.accountIdentity == oldIdentity) {
                        if (runCatching { delete(registration.pushTokenId, oldAccessToken) }.isFailure) return@withLock
                    }
                    clearLocalRegistration()
                }
            }
        }
    }

    fun close() = scope.cancel()

    private suspend fun tokenIdFor(photo: Boolean): Long? = try {
        mutex.withLock {
            val state = deviceState.value
            if (state.accountChanging || !state.deviceStateKnown) return@withLock null
            val enabled = if (photo) state.photoEnabled else state.videoEnabled
            if (!state.notificationPermissionGranted || !enabled) {
                if (!state.photoEnabled && !state.videoEnabled || !state.notificationPermissionGranted) {
                    deregister(accessTokenProvider.getAccessToken())
                }
                return@withLock null
            }
            val token = state.token ?: return@withLock null
            val accessToken = accessTokenProvider.getAccessToken()?.takeIf(String::isNotBlank)
                ?: return@withLock null
            val accountIdentity = accessToken.jwtSubjectOrNull() ?: return@withLock null
            val existing = runCatching { store.read() }.getOrNull()
            if (existing != null && existing.accountIdentity != accountIdentity) clearLocalRegistration()
            val registration = store.read()?.takeIf { it.accountIdentity == accountIdentity }
            if (registration != null && registeredToken == token && registeredAccount == accountIdentity &&
                registeredPreferences == (state.photoEnabled to state.videoEnabled)) {
                return@withLock registration.pushTokenId
            }

            val response = client.put(endpoint) {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(PushTokenRequestDto(token, platform, registration?.pushTokenId,
                    state.photoEnabled, state.videoEnabled))
            }.requireSuccess().body<ApiResponseDto<PushTokenRegistrationDto>>().data
            require(response.pushTokenId > 0L)
            if (!isRegistrationCurrent(token, accountIdentity)) {
                if (response.pushTokenId != registration?.pushTokenId) {
                    runCatching { delete(response.pushTokenId, accessToken) }
                }
                return@withLock null
            }
            val updated = PushTokenRegistration(accountIdentity, response.pushTokenId)
            store.write(updated)
            registeredToken = token
            registeredAccount = accountIdentity
            registeredPreferences = state.photoEnabled to state.videoEnabled
            if (registration != null && registration.pushTokenId != response.pushTokenId) {
                runCatching { delete(registration.pushTokenId, accessToken) }
            }
            response.pushTokenId
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun refreshRejectedTokenId(rejectedId: Long, photo: Boolean): Long? {
        mutex.withLock {
            if (runCatching { store.read()?.pushTokenId }.getOrNull() == rejectedId) {
                clearLocalRegistration()
            }
        }
        return tokenIdFor(photo)
    }

    private suspend fun runCatchingSync() {
        try {
            mutex.withLock {
                val state = deviceState.value
                if (state.accountChanging || !state.deviceStateKnown) return
                val accessToken = accessTokenProvider.getAccessToken()
                if (!state.notificationPermissionGranted || (!state.photoEnabled && !state.videoEnabled)) {
                    deregister(accessToken)
                } else if (state.token != null && accessToken != null) {
                    tokenIdForLocked(state, accessToken)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Registration is best effort and must never block sign-in or content creation.
        }
    }

    private suspend fun tokenIdForLocked(state: DeviceState, accessToken: String): Long? {
        val token = state.token ?: return null
        val identity = accessToken.jwtSubjectOrNull() ?: return null
        val previous = runCatching { store.read() }.getOrNull()
        if (previous != null && previous.accountIdentity != identity) clearLocalRegistration()
        val current = store.read()?.takeIf { it.accountIdentity == identity }
        if (current != null && registeredToken == token && registeredAccount == identity &&
            registeredPreferences == (state.photoEnabled to state.videoEnabled)) return current.pushTokenId
        val response = client.put(endpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(PushTokenRequestDto(token, platform, current?.pushTokenId,
                state.photoEnabled, state.videoEnabled))
        }.requireSuccess().body<ApiResponseDto<PushTokenRegistrationDto>>().data
        require(response.pushTokenId > 0L)
        if (!isRegistrationCurrent(token, identity)) {
            if (response.pushTokenId != current?.pushTokenId) {
                runCatching { delete(response.pushTokenId, accessToken) }
            }
            return null
        }
        store.write(PushTokenRegistration(identity, response.pushTokenId))
        registeredToken = token
        registeredAccount = identity
        registeredPreferences = state.photoEnabled to state.videoEnabled
        if (current != null && current.pushTokenId != response.pushTokenId) {
            runCatching { delete(current.pushTokenId, accessToken) }
        }
        return response.pushTokenId
    }

    private suspend fun isRegistrationCurrent(token: String, accountIdentity: String): Boolean {
        val state = deviceState.value
        return !state.accountChanging && state.token == token && state.notificationPermissionGranted &&
            (state.photoEnabled || state.videoEnabled) &&
            accessTokenProvider.getAccessToken()?.jwtSubjectOrNull() == accountIdentity
    }

    private suspend fun deregister(accessToken: String?) {
        val registration = runCatching { store.read() }.getOrNull()
        val identity = accessToken?.jwtSubjectOrNull()
        if (registration != null && identity != null && registration.accountIdentity == identity) {
            delete(registration.pushTokenId, accessToken)
        }
        clearLocalRegistration()
    }

    private suspend fun delete(pushTokenId: Long, accessToken: String) {
        client.delete(endpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(PushTokenDeleteRequestDto(pushTokenId))
        }.requireSuccess()
    }

    private fun clearLocalRegistration() {
        runCatching { store.clear() }
        registeredToken = null
        registeredAccount = null
        registeredPreferences = null
    }

    private data class DeviceState(
        val token: String? = null,
        val notificationPermissionGranted: Boolean = false,
        val photoEnabled: Boolean = true,
        val videoEnabled: Boolean = true,
        val accountChanging: Boolean = false,
        val deviceStateKnown: Boolean = false,
    )

    private companion object {
        const val AccountSwitchCleanupTimeoutMillis = 2_500L
    }
}
