package com.mapmory.shared.data.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 이슈 #682 실험에서 2장을 동시에 받아도 장당 시간이 늘지 않았다. 3~4장은 측정하지 않았다. */
internal const val DefaultOriginalPrefetchConcurrency = 2

/**
 * 사진 원본을 저장 전에 미리 받아 두는 대기열.
 *
 * - 같은 사진은 한 번만 받는다. 저장이 같은 사진을 읽으면 [awaitPrefetch]로 진행 중인 작업을 기다린다.
 * - 기다리는 쪽이 취소돼도 미리 받기는 취소되지 않는다(Job.join은 대상 작업을 취소하지 않는다).
 * - 미리 받기가 실패하거나 취소돼도 [awaitPrefetch]는 예외 없이 돌아온다. 그러면 호출자가 직접 받는다.
 * - 이미 시작한 다운로드는 플랫폼 API로 취소할 수 없다고 보고, 지우기는 끝나기를 기다린 뒤 한다.
 * - [prefetch], 지우기, [awaitPrefetch] 조회는 부른 순서대로 처리한다. 그래서 막 요청한 미리 받기도
 *   저장이 기다리고, 지우기 요청 뒤에 다시 고른 사진은 지우지 않는다.
 *
 * 상태는 [mutex]로만 다루므로 어느 스레드에서 불러도 된다.
 */
internal class OriginalPrefetchQueue(
    private val scope: CoroutineScope,
    maxConcurrent: Int = DefaultOriginalPrefetchConcurrency,
    private val download: suspend (localId: String) -> Boolean,
    private val delete: suspend (localId: String) -> Unit,
) {
    private class Entry {
        lateinit var job: Job
        var started = false
    }

    private sealed interface Command {
        /** [download] false only records the selection (e.g. Low Data Mode) without fetching. */
        class Prefetch(val localIds: List<String>, val download: Boolean) : Command
        class Release(val localIds: Set<String>, val done: CompletableDeferred<Unit>?) : Command
        class Await(val localId: String, val job: CompletableDeferred<Job?>) : Command
    }

    private val mutex = Mutex()
    private val permits = Semaphore(maxConcurrent)
    private val entries = mutableMapOf<String, Entry>()

    // Order of prefetch requests per photo. A release deletes a photo only if it was not
    // requested again after the release itself was handled.
    private var prefetchSequence = 0L
    private val lastPrefetchSequence = mutableMapOf<String, Long>()

    // Callers queue commands synchronously; one consumer handles them in call order.
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        // ATOMIC: even if the scope is cancelled before the consumer first runs, its finally still
        // closes the channel, so no caller waits on a consumer that never started.
        scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                for (command in commands) {
                    try {
                        when (command) {
                            is Command.Prefetch -> enqueue(command.localIds, command.download)
                            is Command.Release -> startRelease(command.localIds, command.done)
                            is Command.Await -> command.job.complete(mutex.withLock { entries[command.localId]?.job })
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // One failed command must not stop handling the ones after it, nor leave its caller waiting.
                        command.completeUnhandled()
                    }
                }
            } finally {
                // If the consumer ever stops, callers must not wait on it: refuse new commands and
                // answer the queued ones. A save then downloads the original itself.
                commands.close()
                while (true) {
                    commands.tryReceive().getOrNull()?.completeUnhandled() ?: break
                }
            }
        }
    }

    private fun Command.completeUnhandled() {
        when (this) {
            is Command.Release -> done?.complete(Unit)
            is Command.Await -> job.complete(null)
            is Command.Prefetch -> Unit
        }
    }

    fun prefetch(localIds: List<String>) {
        if (localIds.isNotEmpty()) commands.trySend(Command.Prefetch(localIds, download = true))
    }

    /** 받지는 않고 다시 골랐다는 것만 기록한다. 앞서 요청한 지우기가 이 사진의 원본을 지우지 않는다. */
    fun markSelected(localIds: List<String>) {
        if (localIds.isNotEmpty()) commands.trySend(Command.Prefetch(localIds, download = false))
    }

    /** 지우기를 요청하고 바로 돌아온다. 이어서 부른 [prefetch]보다 먼저 처리된다. */
    fun releaseLater(localIds: Set<String>) {
        if (localIds.isNotEmpty()) commands.trySend(Command.Release(localIds, done = null))
    }

    /** [releaseLater]와 같고, 지우기가 끝날 때까지 기다린다. */
    suspend fun release(localIds: Set<String>) {
        if (localIds.isEmpty()) return
        val done = CompletableDeferred<Unit>()
        if (commands.trySend(Command.Release(localIds, done)).isFailure) return
        done.await()
    }

    /**
     * 진행 중이거나 먼저 요청된 미리 받기가 있으면 끝날 때까지 기다리고 true를 돌려준다. 없으면 false.
     * 조회도 대기열을 거치므로, 앞서 [prefetch]한 사진은 아직 등록 전이어도 기다린다.
     */
    suspend fun awaitPrefetch(localId: String): Boolean {
        val lookup = CompletableDeferred<Job?>()
        if (commands.trySend(Command.Await(localId, lookup)).isFailure) return false
        val job = lookup.await() ?: return false
        job.join()
        return true
    }

    private suspend fun enqueue(localIds: List<String>, download: Boolean) = mutex.withLock {
        // Enqueued in the given order; Semaphore hands out permits first-in, first-out.
        localIds.distinct().forEach { id ->
            lastPrefetchSequence[id] = ++prefetchSequence
            if (!download || entries[id] != null) return@forEach
            val entry = Entry()
            entry.job = scope.launch(start = CoroutineStart.LAZY) { run(id, entry) }
            entries[id] = entry
            entry.job.start()
        }
    }

    private suspend fun startRelease(localIds: Set<String>, done: CompletableDeferred<Unit>?) {
        var releaseSequence = 0L
        val running = mutex.withLock {
            releaseSequence = prefetchSequence
            localIds.mapNotNull { id ->
                val entry = entries[id] ?: return@mapNotNull null
                if (entry.started) {
                    entry.job
                } else {
                    // A job cancelled before its body runs never reaches its own cleanup.
                    entries.remove(id)
                    entry.job.cancel()
                    null
                }
            }
        }
        // Waiting for downloads runs aside so later commands are not held up.
        scope.launch(NonCancellable) {
            try {
                running.forEach { it.join() }
                mutex.withLock {
                    // Deleting under the lock keeps a new prefetch from reusing a file being removed.
                    localIds.forEach { id ->
                        val selectedAgain = (lastPrefetchSequence[id] ?: 0L) > releaseSequence
                        if (!selectedAgain) {
                            deleteQuietly(id)
                            lastPrefetchSequence.remove(id)
                        }
                    }
                }
            } finally {
                done?.complete(Unit)
            }
        }
    }

    /** A file that could not be removed stays until the next startup prune; the rest still go. */
    private suspend fun deleteQuietly(id: String) {
        try {
            delete(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }

    private suspend fun run(id: String, entry: Entry) {
        try {
            permits.withPermit {
                mutex.withLock { entry.started = true }
                try {
                    download(id)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The save falls back to downloading the original itself.
                    false
                }
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { if (entries[id] === entry) entries.remove(id) }
            }
        }
    }
}

/**
 * 셀룰러에서도 미리 받는다. 저데이터 모드를 켠 사용자는 데이터를 아끼려는 것이므로 미리 받지 않는다.
 * 저장할 때는 이 정책과 관계없이 원본을 받는다(#682 제품 결정).
 */
internal fun shouldPrefetchOriginals(lowDataMode: Boolean): Boolean = !lowDataMode
