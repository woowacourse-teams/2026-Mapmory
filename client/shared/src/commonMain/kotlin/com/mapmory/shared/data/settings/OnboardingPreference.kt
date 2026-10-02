package com.mapmory.shared.data.settings

interface OnboardingPreference {
    fun hasSeenRecordIntroduction(): Boolean

    fun markRecordIntroductionSeen()
}

class MemoryOnboardingPreference(
    initialHasSeenRecordIntroduction: Boolean = true,
) : OnboardingPreference {
    private var hasSeenRecordIntroduction = initialHasSeenRecordIntroduction

    override fun hasSeenRecordIntroduction(): Boolean = hasSeenRecordIntroduction

    override fun markRecordIntroductionSeen() {
        hasSeenRecordIntroduction = true
    }
}
