package com.mapmory.shared.presentation.auth

import com.mapmory.shared.analytics.*
import com.mapmory.shared.data.auth.*
import com.mapmory.shared.data.remote.MapmoryApiException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.TimeSource

/** App-owned lifetime: SDK callback/server exchange survives leaving the group tab. */
class AccountLoginController(
    private val session: AccountSession,
    private val analytics: MapmoryAnalytics,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutableState = MutableStateFlow(LoginUiState())
    val state = mutableState.asStateFlow()

    fun logout() {
        if (mutableState.value.busy) return
        mutableState.value = LoginUiState(busy = true)
        val provider = session.state.value.member?.provider?.name?.lowercase() ?: "recovery"
        val parameters = mapOf("source" to "settings", "provider" to provider)
        val started = TimeSource.Monotonic.markNow()
        scope.launch {
            val result = session.logout()
            val error = result.exceptionOrNull()
            val policy = error is AccountBusyException || error is AccountTransitionException || error is PendingAccountException
            val outcome = if (result.isSuccess) "success" else if (policy) "blocked" else if (session.state.value.pendingLogout) "pending" else "failure"
            analytics.emit("auth_logout_completed", parameters + mapOf("outcome" to outcome,
                "stage" to session.state.value.stage, "duration_ms" to started.elapsedNow().inWholeMilliseconds.toString()))
            if (result.isSuccess) analytics.emit("logout", parameters)
            else if (!policy) analytics.reportFailure("account_logout", checkNotNull(error), parameters)
            mutableState.value = LoginUiState(message = when {
                result.isSuccess -> null
                policy -> error?.message
                session.state.value.pendingLogout -> "로그아웃을 마무리하지 못했어요. 연결 상태를 확인하고 다시 시도해 주세요."
                else -> "로그아웃을 준비하지 못했어요. 다시 시도해 주세요."
            })
        }
    }

    fun leaveWithdrawalRecovery() {
        if (mutableState.value.busy) return
        mutableState.value = LoginUiState(busy = true)
        scope.launch {
            val result = session.leaveWithdrawalRecovery()
            mutableState.value = LoginUiState(message = result.exceptionOrNull()?.message)
        }
    }

    fun withdraw(launcher: SocialLoginLauncher?) {
        if (mutableState.value.busy) return
        val apple = session.state.value.member?.provider == MemberProvider.APPLE ||
            (session.state.value.pendingWithdrawal && session.state.value.recentLoginProvider == MemberProvider.APPLE)
        mutableState.value = LoginUiState(busy = true)
        fun submit(identityToken: String? = null, authorizationCode: String? = null) {
            scope.launch {
                val result = session.withdraw(identityToken, authorizationCode)
                mutableState.value = LoginUiState(message = when {
                    result.isSuccess -> null
                    result.exceptionOrNull() is WithdrawalUnconfirmedException -> result.exceptionOrNull()?.message
                    else -> "탈퇴를 완료하지 못했어요. 연결 상태를 확인하고 다시 시도해 주세요."
                })
            }
        }
        if (!apple) { submit(); return }
        if (launcher == null || SocialProvider.APPLE !in launcher.providers) {
            mutableState.value = LoginUiState(message = "Apple 재인증을 사용할 수 없어요. 다시 시도해 주세요.")
            return
        }
        try {
            launcher.launch(SocialProvider.APPLE) { result ->
                val credential = (result as? SocialLoginSuccess)?.credential
                if (!credential?.idToken.isNullOrBlank() && !credential?.authorizationCode.isNullOrBlank()) {
                    submit(credential?.idToken, credential?.authorizationCode)
                } else mutableState.value = LoginUiState(message = "Apple 재인증을 완료하지 못했어요. 다시 시도해 주세요.")
            }
        } catch (error: Exception) {
            mutableState.value = LoginUiState(message = "Apple 재인증을 시작하지 못했어요. 다시 시도해 주세요.")
        }
    }

    fun launch(launcher: SocialLoginLauncher, provider: SocialProvider, recoverInvalidSession: Boolean = false) {
        if (!mutableState.compareAndSet(mutableState.value.takeIf { !it.busy } ?: return, LoginUiState(busy = true))) return
        val started = TimeSource.Monotonic.markNow()
        val parameters = mapOf("source" to "group_tab", "provider" to provider.name.lowercase())
        analytics.emit("auth_login_started", parameters)
        val delivered = MutableStateFlow(false)
        try {
            launcher.launch(provider) { result ->
                if (delivered.compareAndSet(false, true)) scope.launch {
                    when (result) {
                        SocialLoginCancelled -> finish("cancelled", "provider_auth", started, parameters)
                        is SocialLoginFailure -> finish("failure", "provider_auth", started, parameters, result.message)
                        is SocialLoginSuccess -> {
                            val token = result.credential.idToken
                            if (result.credential.provider != provider || token.isNullOrBlank()) {
                                val error = IllegalStateException("인증 토큰을 받지 못했어요. 카카오 OpenID Connect 설정을 확인해 주세요.")
                                analytics.reportFailure("social_provider_auth", error, parameters)
                                finish("failure", "provider_auth", started, parameters, error.message)
                            } else complete(session.login(MemberProvider.valueOf(provider.name), token, recoverInvalidSession), started, parameters)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            if (delivered.compareAndSet(false, true)) {
                finish(if (error is CancellationException) "cancelled" else "failure", "provider_auth", started, parameters,
                    if (error is CancellationException) null else "로그인 화면을 열지 못했어요. 다시 시도해 주세요.")
            }
        }
    }

    /** Reauthenticate the current member only; never route through the account-switch login path. */
    fun reauthenticateSameAccount(
        launcher: SocialLoginLauncher,
        provider: SocialProvider,
        onCancelled: () -> Unit,
    ) {
        if (!mutableState.compareAndSet(mutableState.value.takeIf { !it.busy } ?: return, LoginUiState(busy = true))) return
        val expected = session.state.value.sessionProvider
        if (expected == null || expected == MemberProvider.GUEST || expected.name != provider.name) {
            mutableState.value = LoginUiState(message = "현재 계정과 같은 로그인 방식을 확인할 수 없어요.")
            return
        }
        val started = TimeSource.Monotonic.markNow()
        val parameters = mapOf("source" to "auth_recovery", "provider" to provider.name.lowercase())
        val delivered = MutableStateFlow(false)
        try {
            launcher.launch(provider) { result ->
                if (delivered.compareAndSet(false, true)) scope.launch {
                    when (result) {
                        SocialLoginCancelled -> {
                            finish("cancelled", "provider_auth", started, parameters)
                            onCancelled()
                        }
                        is SocialLoginFailure -> {
                            analytics.reportFailure("social_account_reauthentication", IllegalStateException(result.message), parameters)
                            finish("failure", "provider_auth", started, parameters, result.message)
                        }
                        is SocialLoginSuccess -> {
                            val credential = result.credential
                            if (credential.provider != provider || credential.idToken.isNullOrBlank()) {
                                finish("failure", "provider_auth", started, parameters, "인증 정보를 확인하지 못했어요. 다시 시도해 주세요.")
                            } else {
                                val recovered = session.reauthenticateSameAccount(
                                    MemberProvider.valueOf(provider.name),
                                    credential.idToken,
                                )
                                recovered.fold(
                                    { finish("success", "same_account_reauthenticated", started, parameters) },
                                    { error ->
                                        analytics.reportFailure("social_account_reauthentication", error, parameters)
                                        finish("failure", "same_account_reauthenticated", started, parameters,
                                            error.message ?: "현재 계정을 확인하지 못했어요. 요청한 작업은 실행하지 않았습니다.")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            if (delivered.compareAndSet(false, true)) {
                if (error is CancellationException) {
                    finish("cancelled", "provider_auth", started, parameters)
                    onCancelled()
                } else finish("failure", "provider_auth", started, parameters,
                    "로그인 화면을 열지 못했어요. 다시 시도해 주세요.")
            }
        }
    }

    fun retryExistingCredentials() {
        if (!mutableState.compareAndSet(mutableState.value.takeIf { !it.busy } ?: return, LoginUiState(busy = true))) return
        scope.launch {
            val result = session.retryExistingCredentials()
            result.fold(
                { mutableState.value = LoginUiState(message = "저장된 연결을 확인했어요. 원래 작업을 다시 눌러 주세요.") },
                { error -> mutableState.value = LoginUiState(message = when {
                    error is MapmoryApiException && error.statusCode in 500..599 -> "서버에 연결할 수 없어요. 기존 기록은 이 기기에 보관되어 있어요. 연결을 확인하고 다시 시도해 주세요."
                    error is MapmoryApiException && error.statusCode == 401 -> "저장된 연결을 복구하지 못했어요. 기록과 작성 내용은 보관 중이며 새 계정은 만들지 않았어요."
                    else -> "기존 연결을 확인하지 못했어요. 기록과 작성 내용은 보관 중이에요. 연결을 확인하고 다시 시도해 주세요."
                }) },
            )
        }
    }

    fun requestRecoveryPrompt() = session.requestRecoveryPrompt()

    fun resume() {
        if (mutableState.value.busy) return
        mutableState.value = LoginUiState(busy = true)
        val parameters = mapOf("source" to "group_tab", "provider" to "recovery")
        analytics.emit("auth_login_started", parameters)
        scope.launch { complete(session.resumeLogin(), TimeSource.Monotonic.markNow(), parameters) }
    }

    private fun complete(result: Result<AccountMember>, started: TimeSource.Monotonic.ValueTimeMark, parameters: Map<String, String>) {
        val stage = session.state.value.stage
        result.fold({ member ->
            val context = parameters + mapOf("provider" to member.provider.name.lowercase(),
                "guest_abandoned" to session.state.value.guestAbandoned.toString(), "account_linked" to "true")
            finish("success", "completed", started, context)
            analytics.emit("login", context + ("method" to member.provider.name.lowercase()))
        }, { error ->
            val policy = error is AccountBusyException || error is AccountTransitionException || error is PendingAccountException
            if (!policy) analytics.reportFailure("social_account_login", error, parameters + ("stage" to stage))
            finish(if (policy) "blocked" else if (session.state.value.pendingLogin) "pending" else "failure", stage, started, parameters,
                loginErrorMessage(stage, error))
        })
    }

    private fun finish(outcome: String, stage: String, started: TimeSource.Monotonic.ValueTimeMark,
        parameters: Map<String, String>, message: String? = null) {
        analytics.emit("auth_login_completed", parameters + mapOf("outcome" to outcome,
            "stage" to stage, "duration_ms" to started.elapsedNow().inWholeMilliseconds.toString()))
        mutableState.value = LoginUiState(message = message)
    }

    fun close() { scope.cancel() }
}

data class LoginUiState(val busy: Boolean = false, val message: String? = null)

internal fun loginErrorMessage(stage: String, error: Throwable): String = when {
    error is MapmoryApiException && error.code in setOf("INVALID_GOOGLE_TOKEN", "INVALID_KAKAO_TOKEN", "INVALID_APPLE_TOKEN") ->
        "인증 정보를 확인하지 못했어요. 로그인 버튼을 눌러 다시 인증해 주세요."
    error is MapmoryApiException && error.statusCode == 429 -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
    error is MapmoryApiException && error.code in setOf("INVALID_ACCESS_TOKEN", "INVALID_REFRESH_TOKEN", "EXPIRED_REFRESH_TOKEN", "INACTIVE_MEMBER") ->
        "기존 세션을 복구하지 못했어요. 로그인 버튼을 누르면 계정으로 다시 로그인할 수 있어요."
    error is AccountBusyException || error is AccountTransitionException || error is PendingAccountException -> error.message!!
    stage == "token_store" -> "로그인은 확인했지만 기기에 저장하지 못했어요. 앱을 닫지 말고 결과 확인을 다시 눌러 주세요."
    stage == "member_check" -> "로그인 정보를 저장했어요. 계정 확인을 다시 시도해 주세요."
    stage == "account_switch" -> "계정 화면을 준비하지 못했어요. 결과 확인을 다시 시도해 주세요."
    stage == "server_exchange" -> "로그인 결과를 확인하지 못했어요. 결과 확인을 눌러 이어서 진행해 주세요."
    else -> "로그인 정보를 준비하지 못했어요. 연결 상태를 확인한 후 다시 시도해 주세요."
}
