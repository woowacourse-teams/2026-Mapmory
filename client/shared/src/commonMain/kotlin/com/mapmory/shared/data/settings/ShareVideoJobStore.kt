package com.mapmory.shared.data.settings

import com.mapmory.shared.domain.model.ShareVideoStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShareVideoJobState(
    val jobId: Long? = null,
    val status: ShareVideoStatus? = null,
    val notifiedJobId: Long? = null,
    val statusCheckError: String? = null,
    val startedAtEpochMillis: Long? = null,
    val isCreationRequestInProgress: Boolean = false,
    val statusCheckRetryCount: Int = 0,
)

interface ShareVideoJobStore {
    val state: StateFlow<ShareVideoJobState>

    fun loadJobId(): Long?

    fun saveJobId(jobId: Long, startedAtEpochMillis: Long)

    fun tryStartCreationRequest(): Boolean

    fun finishCreationRequest()

    fun updateStatus(status: ShareVideoStatus)

    fun updateStatusCheckError(message: String)

    fun retryStatusCheck()

    fun markCompletionNotified(jobId: Long)

    fun hasCompletionBeenNotified(jobId: Long): Boolean

    fun clear()
}

class MemoryShareVideoJobStore : ShareVideoJobStore {
    private val mutableState = MutableStateFlow(ShareVideoJobState())
    override val state: StateFlow<ShareVideoJobState> = mutableState.asStateFlow()

    override fun loadJobId(): Long? = state.value.jobId

    override fun saveJobId(jobId: Long, startedAtEpochMillis: Long) {
        require(jobId > 0L)
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
        mutableState.value = state.value.copy(notifiedJobId = jobId)
    }

    override fun hasCompletionBeenNotified(jobId: Long): Boolean =
        state.value.notifiedJobId == jobId

    override fun clear() {
        mutableState.value = state.value.copy(
            jobId = null,
            status = null,
            statusCheckError = null,
            startedAtEpochMillis = null,
            isCreationRequestInProgress = false,
        )
    }
}
