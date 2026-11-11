package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

class IosPushTokenRegistrationStore(apiBaseUrl: String) : PushTokenRegistrationStore {
    private val preferences = NSUserDefaults.standardUserDefaults
    private val key = "mapmory_push_registration:${apiBaseUrl.trimEnd('/')}"

    override fun read(): PushTokenRegistration? {
        val identity = preferences.stringForKey("$key:account") ?: return null
        val id = preferences.integerForKey("$key:id").takeIf { it > 0L } ?: return null
        return PushTokenRegistration(identity, id)
    }

    override fun write(registration: PushTokenRegistration) {
        // An interrupted account switch must not pair the new account with the old token id.
        preferences.removeObjectForKey("$key:id")
        preferences.setObject(registration.accountIdentity, forKey = "$key:account")
        preferences.setInteger(registration.pushTokenId, forKey = "$key:id")
    }

    override fun clear() {
        preferences.removeObjectForKey("$key:account")
        preferences.removeObjectForKey("$key:id")
    }
}
