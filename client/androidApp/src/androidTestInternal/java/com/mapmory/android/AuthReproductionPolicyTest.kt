package com.mapmory.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmory.shared.data.auth.AccountMember
import com.mapmory.shared.data.auth.AccountState
import com.mapmory.shared.data.auth.MemberProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthReproductionPolicyTest {
    private val base = AuthReproductionPolicy.BaseUrl
    private val account = AccountState(member = AccountMember("Disposable A", MemberProvider.GUEST), generation = 1)

    @Test fun onlyExactDevelopmentOriginAndRefreshPathAreAllowed() {
        assertTrue(AuthReproductionPolicy.enabled(AuthReproductionPolicy.BaseUrl))
        assertFalse(AuthReproductionPolicy.enabled("https://api.map-mory.com/api/v1"))
        assertFalse(AuthReproductionPolicy.enabled("https://dev-api.map-mory.com.evil/api/v1"))
        assertTrue(AuthReproductionPolicy.isRefresh("${AuthReproductionPolicy.BaseUrl}/auth/token/refresh"))
        assertFalse(AuthReproductionPolicy.isRefresh("${AuthReproductionPolicy.BaseUrl}/members/me"))
        assertFalse(AuthReproductionPolicy.isRefresh("http://dev-api.map-mory.com/api/v1/auth/token/refresh"))
        assertFalse(AuthReproductionPolicy.isRefresh("https://dev-api.map-mory.com:8443/api/v1/auth/token/refresh"))
    }

    @Test fun onlyExactDevelopmentOriginAndMemberPathAreInstrumented() {
        assertTrue(AuthReproductionPolicy.isMe("$base/members/me"))
        assertTrue(AuthReproductionPolicy.isMe("https://dev-api.map-mory.com:443/api/v1/members/me"))
        assertFalse(AuthReproductionPolicy.isMe("https://api.map-mory.com/api/v1/members/me"))
        assertFalse(AuthReproductionPolicy.isMe("https://uploads.example/api/v1/members/me"))
        assertFalse(AuthReproductionPolicy.isMe("https://dev-api.map-mory.com.example/api/v1/members/me"))
        assertFalse(AuthReproductionPolicy.isMe("http://dev-api.map-mory.com/api/v1/members/me"))
        assertFalse(AuthReproductionPolicy.isMe("https://dev-api.map-mory.com:8443/api/v1/members/me"))
        assertFalse(AuthReproductionPolicy.isMe("$base/members/me/"))
        assertFalse(AuthReproductionPolicy.isMe("$base/auth/token/refresh"))
    }

    @Test fun expiryIsDisplayOnlyAndRejectsMalformedPayloads() {
        assertEquals(1234L, AuthReproductionPolicy.expiry("header.eyJleHAiOjEyMzR9.signature"))
        assertNull(AuthReproductionPolicy.expiry("invalid"))
        assertNull(AuthReproductionPolicy.expiry("h.eyJleHAiOiJhYmMifQ.s"))
        assertNull(AuthReproductionPolicy.expiry(null))
    }

    @Test fun revocationRequiresExplicitDisposableConfirmationAndDevelopmentBase() {
        val opening = Any()
        val confirmation = AuthReproductionPolicy.RevocationConfirmation("폐기용 세션", opening, account)
        val blank = AuthReproductionPolicy.RevocationConfirmation("", opening, account)
        assertTrue(AuthReproductionPolicy.canRevoke(base, confirmation, opening, account))
        assertFalse(AuthReproductionPolicy.canRevoke(base, blank, opening, account))
        assertFalse(AuthReproductionPolicy.canRevoke("https://api.map-mory.com/api/v1", confirmation, opening, account))
    }

    @Test fun confirmationExpiresOnDismissalAndReopening() {
        val opening = Any()
        val confirmation = AuthReproductionPolicy.RevocationConfirmation("폐기용 세션", opening, account)
        assertTrue(AuthReproductionPolicy.canRevoke(base, confirmation, opening, account))
        assertFalse(AuthReproductionPolicy.canRevoke(base, confirmation, null, account))
        assertFalse(AuthReproductionPolicy.canRevoke(base, confirmation, Any(), account))
    }

    @Test fun confirmationExpiresOnAccountGenerationOrIdentityChange() {
        val opening = Any()
        val confirmation = AuthReproductionPolicy.RevocationConfirmation("폐기용 세션", opening, account)
        assertFalse(AuthReproductionPolicy.canRevoke(base, confirmation, opening, account.copy(generation = 2)))
        val newMember = account.copy(member = AccountMember("Disposable B", MemberProvider.GUEST))
        assertFalse(AuthReproductionPolicy.canRevoke(base, confirmation, opening, newMember))
    }

    @Test fun revocationRequiresStableAuthenticatedState() {
        val opening = Any()
        val ineligibleAccounts = listOf(
            account.copy(member = null),
            account.copy(transitioning = true),
            account.copy(pendingLogin = true),
            account.copy(pendingLogout = true),
            account.copy(pendingWithdrawal = true),
            account.copy(withdrawalUnconfirmed = true),
            account.copy(recoveryRequired = true),
            account.copy(needsProviderProof = true),
        )
        ineligibleAccounts.forEach { ineligible ->
            val confirmation = AuthReproductionPolicy.RevocationConfirmation("폐기용 세션", opening, ineligible)
            assertFalse(AuthReproductionPolicy.canRevoke(base, confirmation, opening, ineligible))
        }
    }

    @Test fun equalAccountValuesAfterTransitionRequireNewConfirmationOnReopen() {
        val opening = Any()
        val session = MutableStateFlow(account)
        val confirmation = AuthReproductionPolicy.RevocationConfirmation("폐기용 세션", opening, session.value)
        session.value = session.value.copy(transitioning = true)
        session.value = session.value.copy(transitioning = false)
        assertEquals(account, session.value)
        assertFalse(AuthReproductionPolicy.canRevoke(base, confirmation, opening, session.value))

        val reopenedDialog = Any()
        val freshConfirmation = AuthReproductionPolicy.RevocationConfirmation("폐기용 세션", reopenedDialog, session.value)
        assertFalse(AuthReproductionPolicy.canRevoke(base, confirmation, reopenedDialog, session.value))
        assertTrue(AuthReproductionPolicy.canRevoke(base, freshConfirmation, reopenedDialog, session.value))
    }

    @Test fun pauseIsOneShotAndCanResumeWithoutFabricatingTransportResults() = runBlocking<Unit> {
        val gate = RefreshTransmissionGate()
        assertEquals(RefreshTransmissionGate.Result.NOT_ARMED, gate.awaitIfArmed { error("not armed") })
        assertTrue(gate.arm())
        assertFalse(gate.arm())
        val paused = CompletableDeferred<Unit>()
        val request = async { gate.awaitIfArmed { paused.complete(Unit) } }
        paused.await()
        assertEquals(RefreshTransmissionGate.State.PAUSED, gate.state.value)
        assertEquals(RefreshTransmissionGate.Result.NOT_ARMED, gate.awaitIfArmed { error("already claimed") })
        assertTrue(gate.resume())
        assertEquals(RefreshTransmissionGate.Result.RESUMED, request.await())
        assertEquals(RefreshTransmissionGate.State.IDLE, gate.state.value)
        assertFalse(gate.resume())
    }

    @Test fun cancelledAndTimedOutPausesReleaseStateWithoutSending() = runBlocking<Unit> {
        val gate = RefreshTransmissionGate(timeoutMillis = 10)
        assertTrue(gate.arm())
        assertTrue(gate.cancel())
        assertEquals(RefreshTransmissionGate.State.IDLE, gate.state.value)
        assertTrue(gate.arm())
        assertEquals(RefreshTransmissionGate.Result.TIMED_OUT, gate.awaitIfArmed {})
        assertEquals(RefreshTransmissionGate.State.IDLE, gate.state.value)
        assertTrue(gate.arm())
        val paused = CompletableDeferred<Unit>()
        val request = async { gate.awaitIfArmed { paused.complete(Unit) } }
        paused.await()
        assertTrue(gate.cancel())
        assertEquals(RefreshTransmissionGate.Result.CANCELLED, request.await())
        assertEquals(RefreshTransmissionGate.State.IDLE, gate.state.value)
    }

    @Test fun coroutineCancellationDoesNotLeaveAStuckPause() = runBlocking<Unit> {
        val gate = RefreshTransmissionGate()
        gate.arm()
        val paused = CompletableDeferred<Unit>()
        val request = async { gate.awaitIfArmed { paused.complete(Unit) } }
        paused.await()
        request.cancelAndJoin()
        assertEquals(RefreshTransmissionGate.State.IDLE, gate.state.value)
        assertTrue(gate.arm())
        gate.cancel()
    }
}
