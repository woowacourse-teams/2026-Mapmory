package com.mapmory.shared.analytics

import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.settings.ShareVideoJobStore
import com.mapmory.shared.domain.model.ShareVideoJob
import com.mapmory.shared.domain.model.ShareVideoStatus
import com.mapmory.shared.domain.repository.ShareVideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** App-container lifetime; job IDs are deduplication keys only and are never sent to GA. */
internal class AnalyticsShareVideoRepository(
    private val delegate: ShareVideoRepository,
    private val analytics: MapmoryAnalytics,
    private val jobStore: ShareVideoJobStore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ShareVideoRepository {
    private val telemetry = ShareMediaAnalytics(analytics, ShareMediaSource.TRAVEL_TIMELINE)
    private val mutex = Mutex()
    private val statuses = linkedMapOf<Long, ShareVideoStatus>()
    private val reportedTerminalJobs = mutableSetOf<Long>()
    private val startedHere = mutableMapOf<Long, Long>()
    private val queryFailures = mutableMapOf<Long, String>()

    override suspend fun createTravelTimeline(): Result<ShareVideoJob> {
        val started = nowMillis()
        return telemetry.observeResult(ShareMediaOperation.CREATE_REQUEST) {
            analytics.videoActionResult("create_request", delegate::createTravelTimeline)
        }
            .onSuccess { job ->
                mutex.withLock { startedHere[job.jobId] = started }
                observeStatus(job)
            }
    }

    override suspend fun getJob(jobId: Long): Result<ShareVideoJob> {
        val result = try { delegate.getJob(jobId) } catch (error: Exception) {
            queryFailed(jobId, error); throw error
        }
        result.fold(onSuccess = { job ->
            mutex.withLock { queryFailures.remove(jobId) }
            observeStatus(job)
        }, onFailure = { error ->
            queryFailed(jobId, error)
            if (error is CancellationException) throw error
        })
        return result
    }

    override suspend fun getDownloadUrl(jobId: Long) =
        telemetry.observeResult(ShareMediaOperation.DOWNLOAD_URL) { delegate.getDownloadUrl(jobId) }

    private suspend fun queryFailed(jobId: Long, error: Throwable) {
        if (error is CancellationException) return
        mutex.withLock {
            val signature = "${error::class.simpleName}:${(error as? MapmoryApiException)?.statusCode}:${(error as? MapmoryApiException)?.code}"
            if (queryFailures[jobId] == signature) return@withLock
            queryFailures[jobId] = signature
            telemetry.failure(ShareMediaOperation.STATUS_CHECK, error)
            trim()
        }
    }

    private suspend fun observeStatus(job: ShareVideoJob) = mutex.withLock {
        if ((job.status == ShareVideoStatus.COMPLETED || job.status == ShareVideoStatus.FAILED) &&
            reportedTerminalJobs.add(job.jobId)
        ) {
            analytics.emit(MapmoryAnalyticsEvent.VIDEO_GENERATION_RESULT, mapOf(
                "outcome" to if (job.status == ShareVideoStatus.COMPLETED) "success" else "failure",
            ))
        }
        if (statuses[job.jobId] == job.status) return@withLock
        statuses[job.jobId] = job.status
        val started = startedHere[job.jobId] ?: jobStore.state.value
            .takeIf { it.jobId == job.jobId }?.startedAtEpochMillis
        analytics.emit(MapmoryAnalyticsEvent.SHARE_VIDEO_STATUS_CHANGED, telemetry.context + buildMap {
            put("status", job.status.name.lowercase())
            put("origin", if (job.jobId in startedHere) "created_here" else "restored")
            if (job.status == ShareVideoStatus.COMPLETED || job.status == ShareVideoStatus.FAILED) {
                started?.let { put("elapsed_ms", (nowMillis() - it).coerceAtLeast(0L).toString()) }
            }
        })
        if (job.status == ShareVideoStatus.FAILED) {
            telemetry.failure(ShareMediaOperation.GENERATION, ShareVideoGenerationFailedException())
        }
        trim()
    }

    private fun trim() {
        // A bounded process-local history, not a persisted unique-generation counter.
        while (statuses.size > 32) {
            val id = statuses.keys.first(); statuses.remove(id); startedHere.remove(id); queryFailures.remove(id)
        }
        while (queryFailures.size > 32) queryFailures.remove(queryFailures.keys.first())
    }
}

private class ShareVideoGenerationFailedException : Exception("Video generation failed")
