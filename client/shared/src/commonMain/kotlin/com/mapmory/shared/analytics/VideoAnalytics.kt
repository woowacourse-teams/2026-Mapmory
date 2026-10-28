package com.mapmory.shared.analytics

import com.mapmory.shared.domain.model.ShareVideoJob
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.model.ShareVideoStyle
import com.mapmory.shared.domain.repository.ShareVideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Action completion means the platform API returned, not that an external share was delivered. */
internal suspend fun <T> MapmoryAnalytics.videoAction(action: String, block: suspend () -> T): T {
    emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "started"))
    return try {
        block().also {
            emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "success"))
        }
    } catch (error: CancellationException) {
        emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "cancelled"))
        throw error
    } catch (error: Exception) {
        emit(MapmoryAnalyticsEvent.VIDEO_ACTION, mapOf("action" to action, "outcome" to "failure"))
        reportFailure("video_$action", error)
        throw error
    }
}

internal class AnalyticsShareVideoRepository(
    private val delegate: ShareVideoRepository,
    private val analytics: MapmoryAnalytics,
) : ShareVideoRepository by delegate {
    private val mutex = Mutex()
    private val reportedJobs = mutableSetOf<Long>()

    override suspend fun createTravelTimeline(style: ShareVideoStyle): Result<ShareVideoJob> {
        val job = try {
            analytics.videoAction("create_request") { delegate.createTravelTimeline(style).getOrThrow() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(error)
        }
        reportTerminal(job)
        return Result.success(job)
    }

    override suspend fun getJob(jobId: Long): Result<ShareVideoJob> = delegate.getJob(jobId).also { result ->
        result.getOrNull()?.let { reportTerminal(it) }
    }

    private suspend fun reportTerminal(job: ShareVideoJob) = mutex.withLock {
        if (job.status != ShareVideoStatus.COMPLETED && job.status != ShareVideoStatus.FAILED) return@withLock
        if (!reportedJobs.add(job.jobId)) return@withLock
        analytics.emit(MapmoryAnalyticsEvent.VIDEO_GENERATION_RESULT, mapOf(
            "outcome" to if (job.status == ShareVideoStatus.COMPLETED) "success" else "failure",
        ))
    }
}
