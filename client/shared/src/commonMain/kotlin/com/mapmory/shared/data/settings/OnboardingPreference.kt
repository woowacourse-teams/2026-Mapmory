package com.mapmory.shared.data.settings

interface OnboardingPreference {
    fun hasSeenRecordIntroduction(): Boolean

    fun hasSeenVideoPreview(): Boolean

    fun markVideoPreviewSeen()

    fun markRecordIntroductionSeen()
}

class MemoryOnboardingPreference(
    initialHasSeenRecordIntroduction: Boolean = true,
    initialHasSeenVideoPreview: Boolean = false,
) : OnboardingPreference {
    private var hasSeenRecordIntroduction = initialHasSeenRecordIntroduction

    private var hasSeenVideoPreview = initialHasSeenVideoPreview

    override fun hasSeenVideoPreview(): Boolean = hasSeenVideoPreview

    override fun markVideoPreviewSeen() {
        hasSeenVideoPreview = true
    }

    override fun hasSeenRecordIntroduction(): Boolean = hasSeenRecordIntroduction

    override fun markRecordIntroductionSeen() {
        hasSeenRecordIntroduction = true
    }
}
