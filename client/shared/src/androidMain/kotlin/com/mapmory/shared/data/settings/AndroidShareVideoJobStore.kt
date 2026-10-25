package com.mapmory.shared.data.settings

import android.content.Context
import com.mapmory.shared.domain.model.ShareVideoStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidShareVideoJobStore(context: Context, apiBaseUrl: String) : ShareVideoJobStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "mapmory_share_video_jobs",
        Context.MODE_PRIVATE,
    )
    private val key = "job_id:${apiBaseUrl.trimEnd('/')}"
    private val notifiedKey = "notified_job_id:${apiBaseUrl.trimEnd('/')}"
    private val startedAtKey = "started_at:${apiBaseUrl.trimEnd('/')}"
    private val mutableState = MutableStateFlow(
        ShareVideoJobState(
            jobId = preferences.getLong(key, 0L).takeIf { it > 0L },
            notifiedJobId = preferences.getLong(notifiedKey, 0L).takeIf { it > 0L },
            startedAtEpochMillis = preferences.getLong(startedAtKey, 0L).takeIf { it > 0L },
        ),
    )
    override val state: StateFlow<ShareVideoJobState> = mutableState.asStateFlow()

    override fun loadJobId(): Long? = state.value.jobId

    override fun saveJobId(jobId: Long, startedAtEpochMillis: Long) {
        require(jobId > 0L)
        preferences.edit().putLong(key, jobId).putLong(startedAtKey, startedAtEpochMillis).apply()
        mutableState.value = state.value.copy(
            jobId = jobId,
            status = null,
            statusCheckError = null,
            startedAtEpochMillis = startedAtEpochMillis,
            isCreationRequestInProgress = false,
        )
    }

    override fun tryStartCreationRequest(): Boolean {
        while (true) {
            val current = mutableState.value
            if (current.isCreationRequestInProgress ||
                (current.jobId != null && current.status != ShareVideoStatus.FAILED)
            ) return false
            if (mutableState.compareAndSet(current, current.copy(isCreationRequestInProgress = true))) return true
        }
    }

    override fun finishCreationRequest() {
        mutableState.value = state.value.copy(isCreationRequestInProgress = false)
    }

    override fun updateStatus(status: ShareVideoStatus) {
        mutableState.value = state.value.copy(status = status, statusCheckError = null)
    }

    override fun updateStatusCheckError(message: String) {
        mutableState.value = state.value.copy(statusCheckError = message)
    }

    override fun retryStatusCheck() {
        mutableState.value = state.value.copy(statusCheckRetryCount = state.value.statusCheckRetryCount + 1)
    }

    override fun markCompletionNotified(jobId: Long) {
        preferences.edit().putLong(notifiedKey, jobId).apply()
        mutableState.value = state.value.copy(notifiedJobId = jobId)
    }

    override fun hasCompletionBeenNotified(jobId: Long): Boolean =
        state.value.notifiedJobId == jobId

    override fun clear() {
        preferences.edit().remove(key).remove(startedAtKey).apply()
        mutableState.value = state.value.copy(
            jobId = null,
            status = null,
            statusCheckError = null,
            startedAtEpochMillis = null,
            isCreationRequestInProgress = false,
        )
    }
}
