package com.mapmory.android

import com.mapmory.shared.data.auth.AccountState
import io.ktor.http.Url
import org.json.JSONObject
import java.util.Base64

internal object AuthReproductionPolicy {
    const val BaseUrl = "https://dev-api.map-mory.com/api/v1"
    const val Confirmation = "폐기용 세션"

    fun enabled(base: String) = base.trimEnd('/') == BaseUrl

    fun isRefresh(url: String) = matchesDevelopmentPath(url, "/api/v1/auth/token/refresh")

    fun isMe(url: String) = matchesDevelopmentPath(url, "/api/v1/members/me")

    private fun matchesDevelopmentPath(url: String, path: String): Boolean = runCatching {
        val parsed = Url(url)
        parsed.protocol.name == "https" && parsed.host == "dev-api.map-mory.com" &&
            parsed.port == 443 && parsed.encodedPath == path
    }.getOrDefault(false)

    fun expiry(token: String?): Long? = runCatching {
        val payload = token?.split('.')?.takeIf { it.size == 3 }?.get(1) ?: return null
        val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        val value = JSONObject(json).get("exp")
        (value as? Number)?.toLong()?.takeIf { it > 0 }
    }.getOrNull()

    fun hasStableAccount(account: AccountState) =
        account.member != null && !account.transitioning && !account.pendingLogin && !account.pendingLogout &&
            !account.pendingWithdrawal && !account.withdrawalUnconfirmed && !account.recoveryRequired &&
            !account.needsProviderProof

    class RevocationConfirmation(val text: String, private val opening: Any, private val account: AccountState) {
        // A completed transition can restore equal values in a different published state.
        fun isCurrent(opening: Any?, account: AccountState) = this.opening === opening && this.account === account
    }

    fun canRevoke(base: String, confirmation: RevocationConfirmation?, opening: Any?, account: AccountState) =
        enabled(base) && hasStableAccount(account) && confirmation != null &&
            confirmation.text == Confirmation && confirmation.isCurrent(opening, account)
}
