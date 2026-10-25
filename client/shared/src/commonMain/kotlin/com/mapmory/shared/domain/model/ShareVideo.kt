package com.mapmory.shared.domain.model

enum class ShareVideoStatus {
    PENDING,
    RENDERING,
    COMPLETED,
    FAILED,
}

data class ShareVideoJob(
    val jobId: Long,
    val status: ShareVideoStatus,
)

data class ShareVideoDownload(
    val jobId: Long,
    val downloadUrl: String,
    val downloadUrlExpiresIn: Long,
)
