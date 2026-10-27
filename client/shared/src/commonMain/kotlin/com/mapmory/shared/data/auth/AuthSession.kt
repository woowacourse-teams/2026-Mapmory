package com.mapmory.shared.data.auth

import com.mapmory.shared.data.remote.AccessTokenProvider
import kotlinx.coroutines.*
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.remote.MapmoryConnectionException
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString() = "AuthTokens(***)"
    init {
        require(accessToken.isNotBlank()) { "로그인 정보를 확인하지 못했습니다. 다시 로그인해 주세요." }
        require(refreshToken.isNotBlank()) { "로그인 정보를 확인하지 못했습니다. 다시 로그인해 주세요." }
        require('\n' !in accessToken && '\n' !in refreshToken) {
            "로그인 정보를 확인하지 못했습니다. 다시 로그인해 주세요."
        }
    }
}

/** 플랫폼별 영속 저장소를 교체할 수 있도록 만든 인증 토큰 저장 경계다. */
class AuthTokenRead(val tokens: AuthTokens?)
class AuthPendingRead(val provider: String?)

interface AuthTokenStore {
    fun load(): AuthTokens?

    /** Non-null wrapper keeps NSError distinguishable from an absent token across Swift interop. */
    @Throws(Exception::class)
    fun read(): AuthTokenRead = AuthTokenRead(load())

    @Throws(Exception::class)
    fun save(tokens: AuthTokens)

    @Throws(Exception::class)
    fun clear()

    @Throws(Exception::class)
    fun pendingProvider(): AuthPendingRead = AuthPendingRead(null)

    @Throws(Exception::class)
    fun markPendingProvider(provider: String?) = Unit
}

internal interface AuthGateway {
    suspend fun loginAsGuest(): Result<AuthTokens>

    suspend fun refresh(refreshToken: String): Result<AuthTokens>
    suspend fun socialLogin(provider: MemberProvider, idToken: String, accessToken: String?): Result<SocialSessionTokens> =
        Result.failure(UnsupportedOperationException())
    suspend fun member(accessToken: String): Result<AccountMember> = Result.failure(UnsupportedOperationException())
    suspend fun logout(refreshToken: String): Result<Unit> = Result.failure(UnsupportedOperationException())
}

internal class SocialSessionTokens(val tokens: AuthTokens, val guestAbandoned: Boolean)

/**
 * 첫 보호 API 호출 전에 게스트 세션을 준비한다.
 *
 * 저장된 Access Token의 수명이 충분하면 즉시 재사용하고, 만료됐거나 형식을 확인할 수 없을
 * 때만 앱 실행 중 한 번 갱신한다. 저장된 세션이 없으면 게스트 로그인을 한 번 수행한다.
 * Mutex는 동시에 여러 화면이 초기화돼도 회전형 리프레시 토큰이 중복 사용되는 것을 막는다.
 */
internal class GuestSessionManager(
    private val gateway: AuthGateway,
    private val tokenStore: AuthTokenStore,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
    private val elapsedMillis: () -> Long = run { val start = TimeSource.Monotonic.markNow(); { start.elapsedNow().inWholeMilliseconds } },
    private val onRefreshFailed: (stage: String, error: Throwable) -> Unit = { _, _ -> },
) : AccessTokenProvider, AccountSession {
    private val accountState = MutableStateFlow(AccountState())
    override val state: StateFlow<AccountState> = accountState.asStateFlow()
    private val operationMutex = Mutex()
    private var activeOperations = 0
    private var pendingCredential: Pair<MemberProvider, String>? = null
    private var socialTokensReceived = false
    private var pendingAbandoned = false
    private var pendingGeneration = 0L
    private var omitPreviousSession = false
    private var pendingProviderName: String? = null
    private var invalidSessionFailure: Throwable? = null
    internal var canChangeAccount: () -> Boolean = { true }
    internal var onAccountChanged: suspend () -> Unit = {}

    init {
        // Block cache reads before the first network request after an interrupted logout.
        if (runCatching { tokenStore.pendingProvider().provider }.getOrNull()?.startsWith("LOGOUT") == true) {
            accountState.value = AccountState(pendingLogout = true, stage = "logout_recovery")
        }
    }

    /** Keep the lease until uploads AND cache writes finish; refresh uses a separate mutex. */
    internal suspend fun <T> withAuthenticatedSession(request: suspend () -> Result<T>): Result<T> {
        val denied = operationMutex.withLock {
            when {
                state.value.transitioning -> AccountTransitionException()
                state.value.pendingLogout -> PendingLogoutException()
                state.value.pendingLogin -> PendingAccountException()
                invalidSessionFailure != null -> invalidSessionFailure
                else -> { activeOperations++; null }
            }
        }
        if (denied != null) return Result.failure(denied)
        return try {
            ensureAuthenticated().fold({ request() }, { Result.failure(it) }).also { result ->
                val error = result.exceptionOrNull()
                if (error is MapmoryApiException && error.code in setOf("INVALID_ACCESS_TOKEN", "INVALID_REFRESH_TOKEN", "EXPIRED_REFRESH_TOKEN", "INACTIVE_MEMBER")) {
                    invalidSessionFailure = error
                    accountState.value = state.value.copy(recoveryRequired = true)
                }
            }
        }
        finally { withContext(NonCancellable) { operationMutex.withLock { activeOperations-- } } }
    }

    override suspend fun restore(): Result<AccountMember> {
        try {
            val pending = tokenStore.pendingProvider().provider
            if (pending?.startsWith("LOGOUT") == true || state.value.pendingLogout) {
                accountState.value = state.value.copy(pendingLogout = true)
                return Result.failure(PendingLogoutException())
            }
            if (pending != null && !state.value.transitioning && pendingCredential == null && !socialTokensReceived) {
                accountState.value = state.value.copy(pendingLogin = true, stage = "recovery")
                tokens = tokenStore.read().tokens
                didLoadStoredTokens = true
                pendingGeneration = state.value.generation + 1
                pendingProviderName = pending.substringBefore(':')
                pendingAbandoned = pending.endsWith(":abandoned")
                accountState.value = state.value.copy(recoveryProvider = MemberProvider.valueOf(pendingProviderName!!))
                // /me alone is insufficient: a promoted guest's OLD access token may still work.
                if (!pending.contains(":saved")) {
                    omitPreviousSession = true
                    accountState.value = state.value.copy(needsProviderProof = true)
                    return Result.failure(PendingAccountException())
                }
                if (tokens == null) {
                    accountState.value = state.value.copy(needsProviderProof = true)
                    return Result.failure(PendingAccountException())
                }
                socialTokensReceived = true
                return resumeLogin()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        return withAuthenticatedSession {
            gateway.member(checkNotNull(tokens).accessToken).also { result ->
                result.onSuccess { accountState.value = state.value.copy(member = it) }
            }
        }
    }

    override suspend fun login(provider: MemberProvider, idToken: String, recoverInvalidSession: Boolean): Result<AccountMember> {
        require(provider != MemberProvider.GUEST && idToken.isNotBlank())
        return changeAccount(provider to idToken, recoverInvalidSession)
    }

    override suspend fun resumeLogin(): Result<AccountMember> {
        if (!state.value.pendingLogin) return Result.failure(IllegalStateException("진행 중인 로그인이 없어요."))
        if (pendingCredential == null && !socialTokensReceived) return restore()
        return changeAccount(null)
    }

    override suspend fun logout(): Result<Unit> {
        val denied = operationMutex.withLock {
            when {
                state.value.transitioning -> AccountTransitionException()
                activeOperations > 0 || !canChangeAccount() -> AccountBusyException()
                state.value.pendingLogin -> PendingAccountException()
                else -> { accountState.value = state.value.copy(transitioning = true); null }
            }
        }
        if (denied != null) return Result.failure(denied)
        return try {
            withContext(NonCancellable) {
                authenticationMutex.withLock {
                    var marker = tokenStore.pendingProvider().provider
                    if (marker != null && !marker.startsWith("LOGOUT")) return@withLock Result.failure(PendingAccountException())
                    // Read the latest pair without refreshing a token we are about to revoke.
                    val current = if (didLoadStoredTokens) tokens else tokenStore.read().tokens
                    if (!canChangeAccount()) return@withLock Result.failure(AccountBusyException())
                    if (marker == null) {
                        accountState.value = state.value.copy(stage = "logout_prepare")
                        tokenStore.markPendingProvider("LOGOUT")
                        marker = "LOGOUT"
                    }
                    accountState.value = state.value.copy(pendingLogout = true, stage = "logout_server")
                    if (marker != "LOGOUT:revoked" && current != null) {
                        gateway.logout(current.refreshToken).getOrElse { return@withLock Result.failure(it) }
                    }
                    // Revoke is idempotent; a lost response may safely be submitted again.
                    tokenStore.markPendingProvider("LOGOUT:revoked")
                    accountState.value = state.value.copy(stage = "logout_store")
                    tokenStore.clear()
                    tokens = null
                    pendingPersistence = false
                    isAuthenticated = false
                    didLoadStoredTokens = true
                    accountState.value = state.value.copy(stage = "logout_cache")
                    onAccountChanged()
                    tokenStore.markPendingProvider(null)
                    blockedFailure = null
                    invalidSessionFailure = null
                    retryAt = 0
                    didReportRefreshFailure = false
                    pendingCredential = null
                    socialTokensReceived = false
                    pendingProviderName = null
                    omitPreviousSession = false
                    accountState.value = AccountState(transitioning = true,
                        generation = state.value.generation + 1, stage = "logged_out")
                    Result.success(Unit)
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            accountState.value = state.value.copy(transitioning = false)
        }
    }

    private suspend fun changeAccount(credential: Pair<MemberProvider, String>?, recoverInvalidSession: Boolean = false): Result<AccountMember> {
        val denied = operationMutex.withLock {
            when {
                state.value.transitioning -> AccountTransitionException()
                state.value.pendingLogout -> PendingLogoutException()
                activeOperations > 0 || !canChangeAccount() -> AccountBusyException()
                credential != null && state.value.pendingLogin && (pendingCredential != null || socialTokensReceived) -> PendingAccountException()
                credential == null && !state.value.pendingLogin -> IllegalStateException("진행 중인 로그인이 없어요.")
                else -> { accountState.value = state.value.copy(transitioning = true); null }
            }
        }
        if (denied != null) return Result.failure(denied)
        // Once a server mutation can start, a screen cancellation must not lose the new tokens.
        return try {
            withContext(NonCancellable) {
                if (credential != null) {
                    accountState.value = state.value.copy(stage = "session_prepare")
                    val recovering = state.value.pendingLogin
                    if (recovering) {
                        val expected = tokenStore.pendingProvider().provider?.substringBefore(':')
                        if (expected != credential.first.name) return@withContext Result.failure(
                            IllegalStateException("이전에 선택한 로그인 방식으로 다시 인증해 주세요."))
                    } else {
                        val ready = ensureAuthenticated()
                        if (ready.isFailure) {
                            val error = checkNotNull(ready.exceptionOrNull())
                            val invalidSession = error is MapmoryApiException && error.code in setOf(
                                "INVALID_REFRESH_TOKEN", "EXPIRED_REFRESH_TOKEN", "INACTIVE_MEMBER")
                            accountState.value = state.value.copy(recoveryRequired = invalidSession)
                            if (!invalidSession || !recoverInvalidSession) return@withContext Result.failure(error)
                            omitPreviousSession = true
                        }

                    }
                    // A queued save must not slip in while session preparation was suspended.
                    if (!canChangeAccount()) return@withContext Result.failure(AccountBusyException())
                    // Persist recovery intent before any server-side account mutation.
                    pendingProviderName = credential.first.name
                    tokenStore.markPendingProvider(credential.first.name)
                    pendingCredential = credential
                    socialTokensReceived = false
                    pendingGeneration = state.value.generation + 1
                    accountState.value = state.value.copy(pendingLogin = true, recoveryProvider = credential.first, needsProviderProof = false)
                    onAccountChanged()
                }
                finishLogin()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            accountState.value = state.value.copy(transitioning = false)
        }
    }

    private suspend fun finishLogin(): Result<AccountMember> {
        if (!socialTokensReceived) {
            val proof = checkNotNull(pendingCredential)
            accountState.value = state.value.copy(stage = "server_exchange")
            val result = gateway.socialLogin(proof.first, proof.second, if (omitPreviousSession) null else tokens?.accessToken)
            val issued = result.getOrElse { error ->
                // A rejection is definite. Transport/5xx failures may have committed on the server.
                if (error is MapmoryApiException && error.statusCode in 400..499) {
                    // HttpSend already retries EXPIRED_ACCESS_TOKEN at most once.
                    if (error.code in setOf("INACTIVE_MEMBER", "INVALID_REFRESH_TOKEN", "EXPIRED_REFRESH_TOKEN")) {
                        omitPreviousSession = true
                        return Result.failure(error)
                    }
                    if (error.code.startsWith("INVALID_") && error.code.endsWith("_TOKEN") &&
                        error.code !in setOf("INVALID_ACCESS_TOKEN", "INVALID_REFRESH_TOKEN")) {
                        tokenStore.markPendingProvider(null)
                        pendingCredential = null
                        accountState.value = state.value.copy(pendingLogin = false)
                    }
                }
                return Result.failure(error)
            }
            tokens = issued.tokens
            pendingPersistence = true
            socialTokensReceived = true
            pendingAbandoned = issued.guestAbandoned
            pendingCredential = null
            // The old guest refresh is no longer usable. Clear its retry block.
            blockedFailure = null
            invalidSessionFailure = null
            retryAt = 0
        }
        accountState.value = state.value.copy(stage = "token_store")
        persistTokens().getOrElse { return Result.failure(it) }
        tokenStore.markPendingProvider("${checkNotNull(pendingProviderName)}:saved${if (pendingAbandoned) ":abandoned" else ""}")
        accountState.value = state.value.copy(stage = "member_check")
        val member = gateway.member(checkNotNull(tokens).accessToken).getOrElse { return Result.failure(it) }
        if (member.provider.name != pendingProviderName) return Result.failure(IllegalStateException("계정 연결을 확인하지 못했어요."))
        accountState.value = state.value.copy(stage = "account_switch")
        onAccountChanged()
        tokenStore.markPendingProvider(null)
        accountState.value = AccountState(member = member, transitioning = true,
            generation = pendingGeneration, guestAbandoned = pendingAbandoned, stage = "completed")
        socialTokensReceived = false
        omitPreviousSession = false
        return Result.success(member)
    }

    private val authenticationMutex = Mutex()
    private val flightMutex = Mutex()
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var flightKey: String? = null
    private var flight: Deferred<Result<Unit>>? = null
    private var blockedFailure: Throwable? = null
    private var retryAt = 0L

    fun close() { sessionScope.cancel() }

    private suspend fun sharedAuthentication(key: String? = null, block: suspend () -> Result<Unit>): Result<Unit> {
        currentCoroutineContext().ensureActive()
        val work = flightMutex.withLock {
            sessionScope.ensureActive()
            flight?.takeIf { flightKey == key && !it.isCompleted } ?: sessionScope.async {
                block()
            }.also { flight = it; flightKey = key }
        }
        return try {
            work.await()
        } catch (error: CancellationException) {
            // Caller/session cancellation remains cancellation. A cancelled internal worker
            // cannot return a value, so live waiters read its recorded ordinary failure.
            currentCoroutineContext().ensureActive()
            sessionScope.ensureActive()
            authenticationMutex.withLock {
                Result.failure(blockedFailure ?: IllegalStateException("인증 요청이 중단되었습니다. 다시 시도해 주세요.", error))
            }
        }
    }

    private fun refreshBlock(): Throwable? {
        if (blockedFailure != null && elapsedMillis() >= retryAt) blockedFailure = null
        return blockedFailure
    }

    private suspend fun refreshWithRetry(token: String): Result<AuthTokens> {
        refreshBlock()?.let { return Result.failure(it) }
        try {
            repeat(3) { attempt ->
                val result = gateway.refresh(token)
                if (result.isSuccess) return result
                val error = checkNotNull(result.exceptionOrNull())
                if (error is CancellationException) throw error
                val transient = error is MapmoryConnectionException ||
                    (error is MapmoryApiException && error.statusCode in 500..599)
                if (!transient || attempt == 2) {
                    blockedFailure = error
                    retryAt = if (error is MapmoryApiException && error.code in setOf("INVALID_REFRESH_TOKEN", "EXPIRED_REFRESH_TOKEN")) Long.MAX_VALUE else elapsedMillis() + 60_000
                    return result
                }
                retryDelay(1_000L shl attempt)
            }
            error("Unreachable")
        } catch (error: CancellationException) {
            sessionScope.ensureActive()
            // Called under authenticationMutex, including when the worker context itself
            // was cancelled. Record once without suspending or retrying that context.
            val failure = IllegalStateException("인증 요청이 중단되었습니다. 잠시 후 다시 시도해 주세요.", error)
            blockedFailure = failure
            retryAt = elapsedMillis() + 60_000
            isAuthenticated = false
            return Result.failure(failure)
        }
    }
    private var didLoadStoredTokens = false
    private var isAuthenticated = false
    private var pendingPersistence = false
    private var tokens: AuthTokens? = null
    private var didReportRefreshFailure = false

    override fun getAccessToken(): String? = if (state.value.pendingLogout) null else tokens?.accessToken

    suspend fun ensureAuthenticated(): Result<Unit> = sharedAuthentication { ensureSession() }

    private suspend fun ensureSession(): Result<Unit> = authenticationMutex.withLock {
        if (state.value.pendingLogout) return@withLock Result.failure(PendingLogoutException())
        invalidSessionFailure?.let { return@withLock Result.failure(it) }
        refreshBlock()?.let { return@withLock Result.failure(it) }
        if (pendingPersistence) return@withLock persistTokens()
        if (isAuthenticated) return@withLock Result.success(Unit)

        if (!didLoadStoredTokens) {
            try {
                val pending = tokenStore.pendingProvider().provider
                if (pending?.startsWith("LOGOUT") == true) {
                    accountState.value = state.value.copy(pendingLogout = true, stage = "logout_recovery")
                    return@withLock Result.failure(PendingLogoutException())
                }
                if (pending != null) {
                    accountState.value = state.value.copy(pendingLogin = true, stage = "recovery")
                    return@withLock Result.failure(PendingAccountException())
                }
                tokens = tokenStore.read().tokens
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                reportRefreshFailureOnce("storage_read", error)
                return@withLock Result.failure(error)
            }
            didLoadStoredTokens = true
            if (tokens?.accessToken?.hasUsableJwtLifetime(nowEpochSeconds()) == true) {
                isAuthenticated = true
                return@withLock Result.success(Unit)
            }
        }

        val authentication = tokens
            ?.let { refreshWithRetry(it.refreshToken) }
            ?: gateway.loginAsGuest()

        authentication.fold(
            onSuccess = { issuedTokens ->
                acceptIssuedTokens(issuedTokens)
            },
            onFailure = { error ->
                if (tokens != null) reportRefreshFailureOnce("startup", error)
                Result.failure(error)
            },
        )
    }

    /**
     * 보호 API가 401을 반환했을 때 회전형 Refresh Token으로 세션을 한 번 갱신한다.
     *
     * 여러 요청이 같은 만료 Access Token으로 동시에 실패해도 첫 요청만 실제 갱신을
     * 수행한다. 뒤늦게 Mutex를 얻은 요청은 이미 바뀐 Access Token을 그대로 사용한다.
     */
    suspend fun refreshAfterUnauthorized(failedAccessToken: String): Result<Unit> =
        sharedAuthentication(failedAccessToken) { refreshUnauthorized(failedAccessToken) }

    private suspend fun refreshUnauthorized(failedAccessToken: String): Result<Unit> =
        authenticationMutex.withLock {
            if (state.value.pendingLogout) return@withLock Result.failure(PendingLogoutException())
            if (state.value.pendingLogin && socialTokensReceived) return@withLock persistTokens()
            if (pendingPersistence) return@withLock persistTokens()
            val currentTokens = tokens
                ?: return@withLock Result.failure(
                    IllegalStateException("로그인 정보가 만료되었습니다. 다시 로그인해 주세요."),
                )

            if (currentTokens.accessToken != failedAccessToken) {
                return@withLock Result.success(Unit)
            }

            refreshWithRetry(currentTokens.refreshToken).fold(
                onSuccess = { issuedTokens ->
                    acceptIssuedTokens(issuedTokens)
                },
                onFailure = { error ->
                    isAuthenticated = false
                    reportRefreshFailureOnce("unauthorized", error)
                    Result.failure(error)
                },
            )
        }

    private fun acceptIssuedTokens(issuedTokens: AuthTokens): Result<Unit> {
        // A rotated refresh token must never be replaced by the old in-memory token on disk failure.
        tokens = issuedTokens
        pendingPersistence = true
        return persistTokens()
    }

    private fun persistTokens(): Result<Unit> = try {
        tokenStore.save(checkNotNull(tokens))
        pendingPersistence = false
        isAuthenticated = true
        Result.success(Unit)
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        reportRefreshFailureOnce("storage_write", error)
        Result.failure(error)
    }

    private fun reportRefreshFailureOnce(stage: String, error: Throwable) {
        if (didReportRefreshFailure) return
        didReportRefreshFailure = true
        onRefreshFailed(stage, error)
    }
}
