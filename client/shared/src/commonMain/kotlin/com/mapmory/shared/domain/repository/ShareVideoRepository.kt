package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.ShareVideoDownload
import com.mapmory.shared.domain.model.ShareVideoJob
import com.mapmory.shared.domain.model.ShareVideoStyle

interface ShareVideoRepository {
    suspend fun createTravelTimeline(style: ShareVideoStyle): Result<ShareVideoJob>

    suspend fun getJob(jobId: Long): Result<ShareVideoJob>

    suspend fun getDownloadUrl(jobId: Long): Result<ShareVideoDownload>
}
