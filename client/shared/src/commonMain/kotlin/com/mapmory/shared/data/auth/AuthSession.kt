package com.mapmory.shared.data.auth

import com.mapmory.shared.data.remote.AccessTokenProvider
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
    private val onRefreshFailed: (stage: String, error: Throwable) -> Unit = { _, _ -> },
) : AccessTokenProvider {
    private val authenticationMutex = Mutex()
    private var didLoadStoredTokens = false
    private var isAuthenticated = false
    private var pendingPersistence = false
    private var refreshFailure: Throwable? = null
    private var tokens: AuthTokens? = null
    private var didReportRefreshFailure = false

    override fun getAccessToken(): String? = tokens?.accessToken

    suspend fun ensureAuthenticated(): Result<Unit> = authenticationMutex.withLock {
        if (pendingPersistence) return@withLock persistTokens()
        refreshFailure?.let { return@withLock Result.failure(it) }
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
            ?.let { refreshOnce(it.refreshToken) }
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
        authenticationMutex.withLock {
            if (pendingPersistence) return@withLock persistTokens()
            val currentTokens = tokens
                ?: return@withLock Result.failure(
                    IllegalStateException("로그인 정보가 만료되었습니다. 다시 로그인해 주세요."),
                )

            if (currentTokens.accessToken != failedAccessToken) {
                return@withLock Result.success(Unit)
            }

            refreshFailure?.let { return@withLock Result.failure(it) }

            refreshOnce(currentTokens.refreshToken).fold(
                onSuccess = { issuedTokens ->
                    acceptIssuedTokens(issuedTokens)
                },
                onFailure = { error ->
                    reportRefreshFailureOnce("unauthorized", error)
                    Result.failure(error)
                },
            )
        }

    /**
     * A failed/cancelled refresh may already have rotated the server token. Do not replay it
     * from queued requests or polling during this app session. Keep the stored identity so
     * a later app session can recover through the server's response-loss recovery policy.
     */
    private suspend fun refreshOnce(refreshToken: String): Result<AuthTokens> = try {
        gateway.refresh(refreshToken).also { result ->
            result.exceptionOrNull()?.let { refreshFailure = it }
        }
    } catch (error: Exception) {
        refreshFailure = error
        if (error is kotlinx.coroutines.CancellationException) throw error
        Result.failure(error)
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
