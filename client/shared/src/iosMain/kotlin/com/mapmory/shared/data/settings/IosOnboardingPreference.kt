package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

class IosOnboardingPreference : OnboardingPreference {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun hasSeenRecordIntroduction(): Boolean =
        preferences.boolForKey(HasSeenRecordIntroductionKey)

    override fun markRecordIntroductionSeen() {
        preferences.setBool(true, forKey = HasSeenRecordIntroductionKey)
    }
}

private const val HasSeenRecordIntroductionKey = "mapmory_has_seen_record_introduction"
