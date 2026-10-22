package com.mapmory.shared.data.settings

import android.content.Context

class AndroidShareVideoJobStore(context: Context, apiBaseUrl: String) : ShareVideoJobStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "mapmory_share_video_jobs",
        Context.MODE_PRIVATE,
    )
    private val key = "job_id:${apiBaseUrl.trimEnd('/')}"

    override fun loadJobId(): Long? = preferences.getLong(key, 0L).takeIf { it > 0L }

    override fun saveJobId(jobId: Long) {
        require(jobId > 0L)
        preferences.edit().putLong(key, jobId).apply()
    }

    override fun clear() {
        preferences.edit().remove(key).apply()
    }
}
