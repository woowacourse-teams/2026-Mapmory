package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.ShareVideoDownload
import com.mapmory.shared.domain.model.ShareVideoJob

interface ShareVideoRepository {
    suspend fun createTravelTimeline(): Result<ShareVideoJob>

    suspend fun getJob(jobId: Long): Result<ShareVideoJob>

    suspend fun getDownloadUrl(jobId: Long): Result<ShareVideoDownload>
}
