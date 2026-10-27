package com.mapmory.shared.domain.model

enum class ShareVideoStyle(val label: String) {
    CLASSIC("현재 버전"),
    ZOOM("확대 지구본"),
    ORBIT("우주 배경"),
    HORIZON("지평선 지구본"),
}

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
