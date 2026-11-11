package com.mapmory.shared.data.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
 * - 이미 시작한 다운로드는 플랫폼 API로 취소할 수 없다고 보고, [release]는 끝나기를 기다린 뒤 지운다.
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

    private val mutex = Mutex()
    private val permits = Semaphore(maxConcurrent)
    private val entries = mutableMapOf<String, Entry>()

    fun prefetch(localIds: List<String>) {
        if (localIds.isEmpty()) return
        scope.launch {
            mutex.withLock {
                // Enqueued in the given order; Semaphore hands out permits first-in, first-out.
                localIds.distinct().filterNot(entries::containsKey).forEach { id ->
                    val entry = Entry()
                    entry.job = scope.launch(start = CoroutineStart.LAZY) { run(id, entry) }
                    entries[id] = entry
                    entry.job.start()
                }
            }
        }
    }

    /** 진행 중인 미리 받기가 있으면 끝날 때까지 기다리고 true를 돌려준다. 없으면 바로 false. */
    suspend fun awaitPrefetch(localId: String): Boolean {
        val job = mutex.withLock { entries[localId]?.job } ?: return false
        job.join()
        return true
    }

    suspend fun release(localIds: Set<String>) {
        if (localIds.isEmpty()) return
        val running = mutex.withLock {
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
        withContext(NonCancellable) {
            running.forEach { it.join() }
            localIds.forEach { id -> delete(id) }
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
