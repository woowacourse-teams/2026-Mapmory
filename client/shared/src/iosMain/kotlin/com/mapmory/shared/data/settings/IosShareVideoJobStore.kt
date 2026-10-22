package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

class IosShareVideoJobStore(apiBaseUrl: String) : ShareVideoJobStore {
    private val preferences = NSUserDefaults.standardUserDefaults
    private val key = "mapmory_share_video_job_id:${apiBaseUrl.trimEnd('/')}"

    override fun loadJobId(): Long? = preferences.integerForKey(key).takeIf { it > 0L }

    override fun saveJobId(jobId: Long) {
        require(jobId > 0L)
        preferences.setInteger(jobId, forKey = key)
    }

    override fun clear() {
        preferences.removeObjectForKey(key)
    }
}
