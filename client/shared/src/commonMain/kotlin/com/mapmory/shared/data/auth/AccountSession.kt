package com.mapmory.shared.data.auth

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class MemberProvider { GUEST, GOOGLE, KAKAO, APPLE }
@Serializable
data class AccountMember(val nickname: String, val provider: MemberProvider)

data class AccountState(
    val member: AccountMember? = null,
    val transitioning: Boolean = false,
    val pendingLogin: Boolean = false,
    val pendingLogout: Boolean = false,
    val generation: Long = 0,
    val guestAbandoned: Boolean = false,
    val stage: String = "idle",
    val recoveryRequired: Boolean = false,
    val recoveryProvider: MemberProvider? = null,
    val needsProviderProof: Boolean = false,
    val recentLoginProvider: MemberProvider? = null,
)

/** Provider proofs remain in memory only. All account/session changes use this boundary. */
interface AccountSession {
    val state: StateFlow<AccountState>
    suspend fun restore(): Result<AccountMember>
    suspend fun login(provider: MemberProvider, idToken: String, recoverInvalidSession: Boolean = false): Result<AccountMember>
    suspend fun resumeLogin(): Result<AccountMember>
    suspend fun logout(): Result<Unit> = Result.failure(UnsupportedOperationException())
}

internal class AccountTransitionException : IllegalStateException("계정을 연결하고 있어요. 잠시 후 다시 시도해 주세요.")
internal class AccountBusyException : IllegalStateException("진행 중인 요청이나 저장을 마친 후 다시 로그인해 주세요.")
internal class PendingAccountException : IllegalStateException("로그인 결과 확인이 필요해요. 그룹 탭에서 이어서 진행해 주세요.")
internal class PendingLogoutException : IllegalStateException("로그아웃을 마무리해야 해요. 다시 시도해 주세요.")
