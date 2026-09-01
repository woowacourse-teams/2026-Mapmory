package com.mapmory.shared.data.auth

import android.content.Context

class AndroidAuthTokenStore(context: Context) : AuthTokenStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    override fun load(): AuthTokens? = preferences.getString(TokensKey, null)
        ?.toAuthTokensOrNull()

    override fun save(tokens: AuthTokens) {
        check(preferences.edit().putString(TokensKey, tokens.toPayload()).commit()) {
            "인증 토큰을 저장하지 못했습니다."
        }
    }

    override fun clear() {
        check(preferences.edit().remove(TokensKey).commit()) {
            "인증 토큰을 삭제하지 못했습니다."
        }
    }
}

private fun AuthTokens.toPayload(): String = "$accessToken\n$refreshToken"

private fun String.toAuthTokensOrNull(): AuthTokens? {
    val parts = split('\n', limit = 2)
    if (parts.size != 2 || parts.any(String::isBlank)) return null
    return runCatching { AuthTokens(parts[0], parts[1]) }.getOrNull()
}

private const val PreferencesName = "mapmory_auth"
private const val TokensKey = "tokens"
