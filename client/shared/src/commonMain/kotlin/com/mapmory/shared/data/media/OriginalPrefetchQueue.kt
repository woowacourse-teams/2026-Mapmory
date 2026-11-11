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
 * - [prefetch]와 지우기 요청은 부른 순서대로 처리한다. 지우기로 한 사진을 다시 고르면 지우지 않는다.
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

        /** Set by a release of this running download; a later prefetch of the same photo clears it. */
        var releaseRequested = false
    }

    private sealed interface Command {
        class Prefetch(val localIds: List<String>) : Command
        class Release(val localIds: Set<String>, val done: CompletableDeferred<Unit>?) : Command
    }

    private val mutex = Mutex()
    private val permits = Semaphore(maxConcurrent)
    private val entries = mutableMapOf<String, Entry>()

    // Callers queue commands synchronously; one consumer handles them in call order.
    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Prefetch -> enqueue(command.localIds)
                    is Command.Release -> startRelease(command.localIds, command.done)
                }
            }
        }
    }

    fun prefetch(localIds: List<String>) {
        if (localIds.isNotEmpty()) commands.trySend(Command.Prefetch(localIds))
    }

    /** 지우기를 요청하고 바로 돌아온다. 이어서 부른 [prefetch]보다 먼저 처리된다. */
    fun releaseLater(localIds: Set<String>) {
        if (localIds.isNotEmpty()) commands.trySend(Command.Release(localIds, done = null))
    }

    /** [releaseLater]와 같고, 지우기가 끝날 때까지 기다린다. */
    suspend fun release(localIds: Set<String>) {
        if (localIds.isEmpty()) return
        val done = CompletableDeferred<Unit>()
        commands.trySend(Command.Release(localIds, done))
        done.await()
    }

    /** 진행 중인 미리 받기가 있으면 끝날 때까지 기다리고 true를 돌려준다. 없으면 바로 false. */
    suspend fun awaitPrefetch(localId: String): Boolean {
        val job = mutex.withLock { entries[localId]?.job } ?: return false
        job.join()
        return true
    }

    private suspend fun enqueue(localIds: List<String>) = mutex.withLock {
        // Enqueued in the given order; Semaphore hands out permits first-in, first-out.
        localIds.distinct().forEach { id ->
            val existing = entries[id]
            if (existing != null) {
                // Selected again while a release waits for this download: keep the original.
                existing.releaseRequested = false
                return@forEach
            }
            val entry = Entry()
            entry.job = scope.launch(start = CoroutineStart.LAZY) { run(id, entry) }
            entries[id] = entry
            entry.job.start()
        }
    }

    private suspend fun startRelease(localIds: Set<String>, done: CompletableDeferred<Unit>?) {
        val running = mutex.withLock {
            localIds.mapNotNull { id ->
                val entry = entries[id] ?: return@mapNotNull null
                if (entry.started) {
                    entry.releaseRequested = true
                    id to entry
                } else {
                    // A job cancelled before its body runs never reaches its own cleanup.
                    entries.remove(id)
                    entry.job.cancel()
                    null
                }
            }.toMap()
        }
        // Waiting for downloads runs aside so later commands are not held up.
        scope.launch(NonCancellable) {
            try {
                running.values.forEach { it.job.join() }
                mutex.withLock {
                    // Deleting under the lock keeps a new prefetch from reusing a file being removed.
                    localIds.forEach { id ->
                        val selectedAgain = entries[id] != null || running[id]?.releaseRequested == false
                        if (!selectedAgain) delete(id)
                    }
                }
            } finally {
                done?.complete(Unit)
            }
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
