package com.mapmory.shared.data.settings

interface ShareVideoJobStore {
    fun loadJobId(): Long?

    fun saveJobId(jobId: Long)

    fun clear()
}

class MemoryShareVideoJobStore : ShareVideoJobStore {
    private var jobId: Long? = null

    override fun loadJobId(): Long? = jobId

    override fun saveJobId(jobId: Long) {
        this.jobId = jobId
    }

    override fun clear() {
        jobId = null
    }
}
