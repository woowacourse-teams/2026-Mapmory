package com.mapmory.shared.presentation.group

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.emit
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.settings.AlbumShareStore
import com.mapmory.shared.domain.repository.AlbumRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class AlbumShareStatus { RUNNING, SUCCESS, FAILURE, UNKNOWN }

@Serializable
data class AlbumShareOperation(
    val groupId: Long,
    val recordId: Long,
    val targetAlbumId: Long?,
    val status: AlbumShareStatus,
    val resultAlbumId: Long? = null,
)

/** App-owned requests survive navigation. Interrupted requests are never automatically recreated. */
class AlbumShareCoordinator(
    private val repository: AlbumRepository,
    private val store: AlbumShareStore,
    private val scope: CoroutineScope,
    private val analytics: MapmoryAnalytics,
    private val canStart: () -> Boolean = { true },
) {
    private val mutex = Mutex()
    private var accountEpoch = 0L
    private val groupEpochs = mutableMapOf<Long, Long>()
    private val mutableStorageError = MutableStateFlow<String?>(null)
    val storageError = mutableStorageError.asStateFlow()
    private val mutableOperations = MutableStateFlow(
        try {
            store.read()?.let { Json.decodeFromString<List<AlbumShareOperation>>(it) }.orEmpty().map {
                if (it.status == AlbumShareStatus.RUNNING) it.copy(status = AlbumShareStatus.UNKNOWN) else it
            }
        } catch (error: Exception) {
            mutableStorageError.value = "이전 공유 상태를 읽지 못했어요. 앱을 다시 실행해 주세요."
            emptyList()
        },
    )
    val operations = mutableOperations.asStateFlow()
    val isRunning: Boolean get() = operations.value.any { it.status == AlbumShareStatus.RUNNING }

    fun start(groupId: Long, recordId: Long, albumId: Long?) {
        // Check before dispatch as well, so a queued operation cannot cross an account transition.
        if (!canStart() || storageError.value != null) return
        val epoch = accountEpoch
        val groupEpoch = groupEpochs[groupId] ?: 0L
        scope.launch {
            mutex.withLock {
                if (epoch != accountEpoch || groupEpoch != (groupEpochs[groupId] ?: 0L) || !canStart() || storageError.value != null) return@withLock
                val previous = operations.value.find { it.groupId == groupId && it.recordId == recordId && it.targetAlbumId == albumId }
                if (previous?.status in listOf(AlbumShareStatus.RUNNING, AlbumShareStatus.SUCCESS) ||
                    (albumId == null && previous?.status == AlbumShareStatus.UNKNOWN)) return@withLock
                val operation = AlbumShareOperation(groupId, recordId, albumId, AlbumShareStatus.RUNNING)
                // Persist before sending the write. A process death now requires explicit recovery.
                if (!replace(operation)) return@withLock
                val parameters = mapOf("input_type" to "existing_record", "target_type" to if (albumId == null) "new_album" else "existing_album", "execution_mode" to "live")
                analytics.emit("album_share_started", parameters)
                try {
                    val result = if (albumId == null) repository.create(groupId, recordId) else repository.addRecord(groupId, albumId, recordId)
                    val completed = result.fold(
                        { operation.copy(status = AlbumShareStatus.SUCCESS, resultAlbumId = it.id) },
                        { operation.copy(status = if (it !is MapmoryApiException || it.statusCode >= 500) AlbumShareStatus.UNKNOWN else AlbumShareStatus.FAILURE) },
                    )
                    if (epoch != accountEpoch) return@withLock
                    if (!replace(completed)) {
                        mutableOperations.value = operations.value.map { if (it == operation) it.copy(status = AlbumShareStatus.UNKNOWN) else it }
                    }
                    analytics.emit("album_share_result", parameters + ("result" to when (completed.status) {
                        AlbumShareStatus.SUCCESS -> "success"
                        AlbumShareStatus.UNKNOWN -> "unknown"
                        else -> "failure"
                    }))
                } catch (error: CancellationException) {
                    // The RUNNING journal entry also restores as UNKNOWN after process termination.
                    if (epoch == accountEpoch) replace(operation.copy(status = AlbumShareStatus.UNKNOWN))
                    throw error
                } catch (error: Exception) {
                    if (epoch == accountEpoch) replace(operation.copy(status = AlbumShareStatus.UNKNOWN))
                }
            }
        }
    }

    /** Explicit acknowledgement after checking the server list; this never repeats a write. */
    fun acknowledge(operation: AlbumShareOperation) {
        if (operation.status == AlbumShareStatus.RUNNING) return
        save(operations.value.filterNot { it == operation })
    }

    fun clearForAccountChange() {
        accountEpoch++
        groupEpochs.clear()
        // A storage failure must block the account transition instead of exposing old jobs.
        check(save(emptyList())) { "공유 상태를 정리하지 못했어요. 다시 시도해 주세요." }
    }

    /** A confirmed departure invalidates queued shares and obsolete recovery actions for this group. */
    fun forgetGroup(groupId: Long) {
        groupEpochs[groupId] = (groupEpochs[groupId] ?: 0L) + 1
        save(operations.value.filterNot { it.groupId == groupId })
    }

    private fun replace(operation: AlbumShareOperation) = save(operations.value.filterNot {
        it.groupId == operation.groupId && it.recordId == operation.recordId && it.targetAlbumId == operation.targetAlbumId
    } + operation)

    private fun save(value: List<AlbumShareOperation>): Boolean = try {
        store.write(Json.encodeToString(value))
        mutableOperations.value = value
        mutableStorageError.value = null
        true
    } catch (error: Exception) {
        mutableStorageError.value = "공유 상태를 저장하지 못했어요. 앱을 다시 실행해 주세요."
        false
    }
}
