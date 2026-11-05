package com.mapmory.shared.data.settings

import android.content.Context

class AndroidOnboardingPreference(context: Context) : OnboardingPreference {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    override fun hasSeenVideoPreview(): Boolean =
        preferences.getBoolean(HasSeenVideoPreviewKey, false)

    override fun markVideoPreviewSeen() {
        preferences.edit().putBoolean(HasSeenVideoPreviewKey, true).apply()
    }

    override fun hasSeenRecordIntroduction(): Boolean =
        preferences.getBoolean(HasSeenRecordIntroductionKey, false)

    override fun markRecordIntroductionSeen() {
        preferences.edit().putBoolean(HasSeenRecordIntroductionKey, true).apply()
    }
}

private const val PreferencesName = "mapmory_settings"
private const val HasSeenRecordIntroductionKey = "has_seen_record_introduction"

private const val HasSeenVideoPreviewKey = "mapmory_has_seen_video_preview"
