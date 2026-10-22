package com.mapmory.shared.data.settings

import com.mapmory.shared.domain.model.ShareVideoStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShareVideoJobState(
    val jobId: Long? = null,
    val status: ShareVideoStatus? = null,
    val notifiedJobId: Long? = null,
)

interface ShareVideoJobStore {
    val state: StateFlow<ShareVideoJobState>

    fun loadJobId(): Long?

    fun saveJobId(jobId: Long)

    fun updateStatus(status: ShareVideoStatus)

    fun markCompletionNotified(jobId: Long)

    fun hasCompletionBeenNotified(jobId: Long): Boolean

    fun clear()
}

class MemoryShareVideoJobStore : ShareVideoJobStore {
    private val mutableState = MutableStateFlow(ShareVideoJobState())
    override val state: StateFlow<ShareVideoJobState> = mutableState.asStateFlow()

    override fun loadJobId(): Long? = state.value.jobId

    override fun saveJobId(jobId: Long) {
        require(jobId > 0L)
        mutableState.value = state.value.copy(jobId = jobId, status = null)
    }

    override fun updateStatus(status: ShareVideoStatus) {
        mutableState.value = state.value.copy(status = status)
    }

    override fun markCompletionNotified(jobId: Long) {
        mutableState.value = state.value.copy(notifiedJobId = jobId)
    }

    override fun hasCompletionBeenNotified(jobId: Long): Boolean =
        state.value.notifiedJobId == jobId

    override fun clear() {
        mutableState.value = state.value.copy(jobId = null, status = null)
    }
}
