package com.mapmory.shared.data.settings

import android.content.Context

class AndroidPushTokenRegistrationStore(context: Context, apiBaseUrl: String) : PushTokenRegistrationStore {
    private val preferences = context.applicationContext.getSharedPreferences("mapmory_push_registration", Context.MODE_PRIVATE)
    private val accountKey = "account_identity:${apiBaseUrl.trimEnd('/')}"
    private val tokenIdKey = "push_token_id:${apiBaseUrl.trimEnd('/')}"

    override fun read(): PushTokenRegistration? {
        val accountIdentity = preferences.getString(accountKey, null)?.takeIf(String::isNotBlank) ?: return null
        val pushTokenId = preferences.getLong(tokenIdKey, 0L).takeIf { it > 0L } ?: return null
        return PushTokenRegistration(accountIdentity, pushTokenId)
    }

    override fun write(registration: PushTokenRegistration) {
        require(registration.accountIdentity.isNotBlank() && registration.pushTokenId > 0L)
        preferences.edit()
            .putString(accountKey, registration.accountIdentity)
            .putLong(tokenIdKey, registration.pushTokenId)
            .apply()
    }

    override fun clear() {
        preferences.edit().remove(accountKey).remove(tokenIdKey).apply()
    }
}
