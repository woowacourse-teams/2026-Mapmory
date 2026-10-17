package com.mapmory.shared.app

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.analytics.NoOpMapmoryAnalytics
import com.mapmory.shared.data.media.PhotoPreviewCache
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.ProgressReportingTripRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import kotlin.math.ceil

enum class BackgroundSaveStatus {
    QUEUED,
    SAVING,
    FAILED,
}

data class BackgroundTripRecordSave(
    val id: Long,
    val title: String,
    val locationName: String,
    val startDate: String,
    val photoCount: Int,
    val status: BackgroundSaveStatus,
    val progressPercent: Int = 0,
    val remainingSeconds: Long? = null,
    val errorMessage: String? = null,
)

interface BackgroundSaveExecution {
    val store: BackgroundSaveStore get() = EmptyBackgroundSaveStore
    fun attach(saver: BackgroundTripRecordSaver) {}
    fun onQueued() {}
    fun onProgress(save: BackgroundTripRecordSave) {}
    fun onCompleted(save: BackgroundTripRecordSave) {}
    suspend fun <T> run(block: suspend () -> T): T
}

object DirectBackgroundSaveExecution : BackgroundSaveExecution {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

class BackgroundTripRecordSaver internal constructor(
    private val repository: TripRecordRepository,
    private val scope: CoroutineScope,
    private val execution: BackgroundSaveExecution = DirectBackgroundSaveExecution,
    private val photoPreviewCache: PhotoPreviewCache? = null,
    private val onSaved: () -> Unit = {},
    private val automaticRetryDelayMillis: Long = AutomaticRetryDelayMillis,
    private val localPhotoDataSource: com.mapmory.shared.data.media.LocalPhotoDataSource? = null,
    private val analytics: MapmoryAnalytics = NoOpMapmoryAnalytics,
) {
    private val queueMutex = Mutex()
    private val serverSaveStarted = mutableSetOf<Long>()
    private val drafts = mutableMapOf<Long, TripRecordDraft>()
    private val mutableSaves = MutableStateFlow<List<BackgroundTripRecordSave>>(emptyList())
    private var nextId = 0L
    private val attemptStarts = mutableMapOf<Long, TimeSource.Monotonic.ValueTimeMark>()
    private val queuedAt = mutableMapOf<Long, TimeSource.Monotonic.ValueTimeMark>()

    val saves: StateFlow<List<BackgroundTripRecordSave>> = mutableSaves.asStateFlow()
    private val mutablePendingPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingPhotoIds: StateFlow<Set<String>> = mutablePendingPhotoIds.asStateFlow()

    private fun updatePendingPhotoIds() {
        mutablePendingPhotoIds.value = drafts.values.flatMap { draft ->
            draft.localMedia.mapNotNull { it.localPreviewKey }
        }.toSet()
    }

    init {
        execution.store.read().forEach { pending ->
            nextId = maxOf(nextId, pending.id)
            drafts[pending.id] = pending.draft
            if (pending.serverSaveStarted) serverSaveStarted += pending.id
            mutableSaves.value += BackgroundTripRecordSave(
                id = pending.id, title = pending.draft.title.ifBlank { "사진 업로드 중" },
                locationName = pending.locationName, startDate = pending.draft.startDate,
                photoCount = pending.draft.mediaObjectKeys.size,
                status = if (pending.serverSaveStarted) BackgroundSaveStatus.FAILED else BackgroundSaveStatus.QUEUED,
                errorMessage = if (pending.serverSaveStarted) "저장 결과를 확인하지 못했어요. 목록에 기록이 있는지 확인한 후 다시 시도해 주세요." else null,
            )
        }
        updatePendingPhotoIds()
        execution.attach(this)
        mutableSaves.value.filter { it.status == BackgroundSaveStatus.QUEUED }.forEach { launch(it.id) }
    }

    private fun persist() {
        execution.store.write(mutableSaves.value.mapNotNull { save ->
            drafts[save.id]?.let { PendingTripRecordSave(save.id, save.locationName, it, save.id in serverSaveStarted) }
        })
    }

    fun enqueue(draft: TripRecordDraft, locationName: String): Long {
        val id = ++nextId
        drafts[id] = draft
        queuedAt[id] = TimeSource.Monotonic.markNow()
        updatePendingPhotoIds()
        mutableSaves.update { current ->
            current + BackgroundTripRecordSave(
                id = id,
                title = draft.title.ifBlank { "사진 업로드 중" },
                locationName = locationName,
                startDate = draft.startDate,
                photoCount = draft.mediaObjectKeys.size,
                status = BackgroundSaveStatus.QUEUED,
            )
        }
        persist()
        execution.onQueued()
        launch(id)
        return id
    }

    fun retry(id: Long) {
        if (drafts[id] == null) return
        val failed = mutableSaves.value.firstOrNull { save -> save.id == id } ?: return
        if (failed.status != BackgroundSaveStatus.FAILED) return
        serverSaveStarted.remove(id)
        queuedAt[id] = TimeSource.Monotonic.markNow()
        persist()
        mutableSaves.update { current ->
            current.map { save ->
                if (save.id == id) {
                    save.copy(
                        status = BackgroundSaveStatus.QUEUED,
                        progressPercent = 0,
                        remainingSeconds = null,
                        errorMessage = null,
                    )
                }
                else save
            }
        }
        execution.onQueued()
        launch(id)
    }

    fun dismissFailure(id: Long) {
        if (mutableSaves.value.none { it.id == id && it.status == BackgroundSaveStatus.FAILED }) return
        val discarded = drafts.remove(id)
        serverSaveStarted.remove(id)
        attemptStarts.remove(id)
        queuedAt.remove(id)
        updatePendingPhotoIds()
        mutableSaves.update { current -> current.filterNot { save -> save.id == id } }
        persist()
        scope.launch {
            queueMutex.withLock {
                discarded?.mediaObjectKeys?.distinct()?.forEach { localId ->
                    if (drafts.values.none { localId in it.mediaObjectKeys }) {
                        try {
                            localPhotoDataSource?.release(localId)
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // Continue releasing the remaining files if one cleanup fails.
                        }
                    }
                }
            }
        }
    }

    private fun launch(id: Long) {
        scope.launch {
            queueMutex.withLock {
                try {
                    save(id)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logSaveResult(id, MapmoryAnalyticsEvent.RECORD_SAVE_FAILED, error)
                    mutableSaves.update { saves -> saves.map { save ->
                        if (save.id == id) save.copy(status = BackgroundSaveStatus.FAILED, errorMessage = error.toBackgroundSaveMessage()) else save
                    } }
                }
            }
        }
    }

    private suspend fun save(id: Long) {
        val draft = prepareDraft(id) ?: return
        mutableSaves.update { current ->
            current.map { save ->
                if (save.id == id) {
                    save.copy(
                        status = BackgroundSaveStatus.SAVING,
                        progressPercent = 0,
                        remainingSeconds = null,
                        errorMessage = null,
                    )
                }
                else save
            }
        }

        val result = execution.run {
            var latest = create(id, draft)
            if (latest.isFailure && id !in serverSaveStarted) {
                delay(automaticRetryDelayMillis)
                latest = create(id, draft)
            }
            latest
        }

        result.fold(
            onSuccess = {
                logSaveResult(id, MapmoryAnalyticsEvent.RECORD_SAVE_COMPLETED)
                val completed = mutableSaves.value.firstOrNull { it.id == id }
                attemptStarts.remove(id)
                queuedAt.remove(id)
                drafts.remove(id)
                serverSaveStarted.remove(id)
                updatePendingPhotoIds()
                mutableSaves.update { current -> current.filterNot { save -> save.id == id } }
                persist()
                completed?.let(execution::onCompleted)
                onSaved()
            },
            onFailure = { error ->
                logSaveResult(id, MapmoryAnalyticsEvent.RECORD_SAVE_FAILED, error)
                val message = error.toBackgroundSaveMessage()
                mutableSaves.update { current ->
                    current.map { save ->
                        if (save.id == id) {
                            save.copy(status = BackgroundSaveStatus.FAILED, errorMessage = message)
                        } else {
                            save
                        }
                    }
                }
            },
        )
    }

    private suspend fun prepareDraft(id: Long): TripRecordDraft? {
        val draft = drafts[id] ?: return null
        photoPreviewCache?.let { cache ->
            draft.localMedia.forEach { media ->
                media.previewBytes?.let { bytes ->
                    val cacheKey = media.localPreviewKey ?: media.objectKey
                    cache.write(cacheKey, bytes)
                }
            }
        }
        return draft.copy(
            localMedia = draft.localMedia.map { media ->
                media.copy(previewBytes = null)
            },
        ).also { compact ->
            drafts[id] = compact
            persist()
        }
    }

    private suspend fun create(id: Long, draft: TripRecordDraft): Result<com.mapmory.shared.domain.model.TripRecordData> {
        attemptStarts[id] = TimeSource.Monotonic.markNow()
        updateProgress(id, 0)
        return (repository as? ProgressReportingTripRecordRepository)?.createTripRecord(draft) { progress ->
            updateProgress(id, progress)
        } ?: repository.createTripRecord(draft)
    }

    private fun updateProgress(id: Long, progress: Int) {
        val bounded = progress.coerceIn(0, 100)
        if (bounded >= 95 && serverSaveStarted.add(id)) persist()
        val elapsed = attemptStarts[id]?.elapsedNow()?.inWholeMilliseconds ?: 0L
        val remaining = estimateUploadRemainingSeconds(elapsed, bounded)
        mutableSaves.update { current ->
            current.map { save ->
                if (save.id == id) save.copy(progressPercent = bounded, remainingSeconds = remaining) else save
            }
        }
        mutableSaves.value.firstOrNull { it.id == id }?.let(execution::onProgress)
    }

    // 편집 화면의 저장 이벤트와 같은 이름·파라미터로 자동 재시도까지 끝난 최종 결과만 남긴다.
    private fun logSaveResult(id: Long, event: String, error: Throwable? = null) {
        val draft = drafts[id] ?: return
        val parameters = buildMap {
            put("mode", "create")
            put("save_mode", "background")
            put("has_title", draft.title.isNotBlank().toString())
            put("has_content", (!draft.content.isNullOrBlank()).toString())
            put("has_end_date", (!draft.endDate.isNullOrBlank()).toString())
            put("has_tags", draft.tagIds.isNotEmpty().toString())
            put("has_photos", draft.mediaObjectKeys.isNotEmpty().toString())
            queuedAt[id]?.let { put("duration_ms", it.elapsedNow().inWholeMilliseconds.toString()) }
            error?.let { put("error_type", it::class.simpleName ?: "unknown") }
        }
        analytics.logEvent(event, parameters)
    }
}

private fun Throwable.toBackgroundSaveMessage(): String {
    val fieldErrors = (this as? MapmoryApiException)
        ?.errors
        .orEmpty()
        .map { error -> error.detail.trim() }
        .filter(String::isNotEmpty)
        .distinct()
    return fieldErrors.takeIf { errors -> errors.isNotEmpty() }?.joinToString("\n")
        ?: message
        ?: DefaultFailureMessage
}

private const val AutomaticRetryDelayMillis = 1_000L
private const val DefaultFailureMessage = "기록 저장에 실패했어요. 앱에서 다시 시도해 주세요."

// No estimate before a completed unit; iCloud acquisition time is included in elapsed time.
internal fun estimateUploadRemainingSeconds(elapsedMillis: Long, progressPercent: Int): Long? {
    if (elapsedMillis < 1_000 || progressPercent !in 1..94) return null
    return ceil(elapsedMillis.toDouble() * (100 - progressPercent) / progressPercent / 1_000)
        .toLong().coerceAtLeast(1)
}
