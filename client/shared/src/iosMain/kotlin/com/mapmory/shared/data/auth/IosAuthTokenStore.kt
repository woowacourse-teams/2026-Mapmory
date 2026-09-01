package com.mapmory.shared.data.auth

import platform.Foundation.NSUserDefaults

class IosAuthTokenStore : AuthTokenStore {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun load(): AuthTokens? = preferences.stringForKey(TokensKey)
        ?.toAuthTokensOrNull()

    override fun save(tokens: AuthTokens) {
        preferences.setObject(tokens.toPayload(), forKey = TokensKey)
    }

    override fun clear() {
        preferences.removeObjectForKey(TokensKey)
    }
}

private fun AuthTokens.toPayload(): String = "$accessToken\n$refreshToken"

private fun String.toAuthTokensOrNull(): AuthTokens? {
    val parts = split('\n', limit = 2)
    if (parts.size != 2 || parts.any(String::isBlank)) return null
    return runCatching { AuthTokens(parts[0], parts[1]) }.getOrNull()
}

private const val TokensKey = "mapmory_auth_tokens"
