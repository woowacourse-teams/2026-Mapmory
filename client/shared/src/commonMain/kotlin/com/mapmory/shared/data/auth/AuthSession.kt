package com.mapmory.shared.data.auth

import com.mapmory.shared.data.remote.AccessTokenProvider
import kotlinx.coroutines.*
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.remote.MapmoryConnectionException
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
) {
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

interface AuthTokenStore {
    fun load(): AuthTokens?

    /** Non-null wrapper keeps NSError distinguishable from an absent token across Swift interop. */
    @Throws(Exception::class)
    fun read(): AuthTokenRead = AuthTokenRead(load())

    @Throws(Exception::class)
    fun save(tokens: AuthTokens)

    fun clear()
}

internal interface AuthGateway {
    suspend fun loginAsGuest(): Result<AuthTokens>

    suspend fun refresh(refreshToken: String): Result<AuthTokens>
}

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
) : AccessTokenProvider {
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

    override fun getAccessToken(): String? = tokens?.accessToken

    suspend fun ensureAuthenticated(): Result<Unit> = sharedAuthentication { ensureSession() }

    private suspend fun ensureSession(): Result<Unit> = authenticationMutex.withLock {
        refreshBlock()?.let { return@withLock Result.failure(it) }
        if (pendingPersistence) return@withLock persistTokens()
        if (isAuthenticated) return@withLock Result.success(Unit)

        if (!didLoadStoredTokens) {
            try {
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
