package com.mapmory.shared.data.settings

import android.content.Context

class AndroidOnboardingPreference(context: Context) : OnboardingPreference {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    override fun hasSeenRecordIntroduction(): Boolean =
        preferences.getBoolean(HasSeenRecordIntroductionKey, false)

    override fun markRecordIntroductionSeen() {
        preferences.edit().putBoolean(HasSeenRecordIntroductionKey, true).apply()
    }
}

private const val PreferencesName = "mapmory_settings"
private const val HasSeenRecordIntroductionKey = "has_seen_record_introduction"
