package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults
import com.mapmory.shared.domain.model.ShareVideoStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosShareVideoJobStore(apiBaseUrl: String) : ShareVideoJobStore {
    private val preferences = NSUserDefaults.standardUserDefaults
    private val key = "mapmory_share_video_job_id:${apiBaseUrl.trimEnd('/')}"
    private val notifiedKey = "mapmory_share_video_notified_job_id:${apiBaseUrl.trimEnd('/')}"
    private val mutableState = MutableStateFlow(
        ShareVideoJobState(
            jobId = preferences.integerForKey(key).takeIf { it > 0L },
            notifiedJobId = preferences.integerForKey(notifiedKey).takeIf { it > 0L },
        ),
    )
    override val state: StateFlow<ShareVideoJobState> = mutableState.asStateFlow()

    override fun loadJobId(): Long? = state.value.jobId

    override fun saveJobId(jobId: Long) {
        require(jobId > 0L)
        preferences.setInteger(jobId, forKey = key)
        mutableState.value = state.value.copy(jobId = jobId, status = null)
    }

    override fun updateStatus(status: ShareVideoStatus) {
        mutableState.value = state.value.copy(status = status)
    }

    override fun markCompletionNotified(jobId: Long) {
        preferences.setInteger(jobId, forKey = notifiedKey)
        mutableState.value = state.value.copy(notifiedJobId = jobId)
    }

    override fun hasCompletionBeenNotified(jobId: Long): Boolean =
        state.value.notifiedJobId == jobId

    override fun clear() {
        preferences.removeObjectForKey(key)
        mutableState.value = state.value.copy(jobId = null, status = null)
    }
}
