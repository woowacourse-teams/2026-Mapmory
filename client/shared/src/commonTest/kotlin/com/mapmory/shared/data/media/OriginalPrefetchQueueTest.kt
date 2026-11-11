package com.mapmory.shared.data.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginalPrefetchQueueTest {
    /** Downloads block until the test finishes them, so each step of the queue is observable. */
    private class FakeOriginalStore {
        private val gates = mutableMapOf<String, CompletableDeferred<Boolean>>()
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var running = 0
        var maxRunning = 0

        suspend fun download(id: String): Boolean {
            started += id
            running += 1
            maxRunning = maxOf(maxRunning, running)
            try {
                return gate(id).await().also { completed += id }
            } finally {
                running -= 1
            }
        }

        val failingDeletes = mutableSetOf<String>()

        suspend fun delete(id: String) {
            if (id in failingDeletes) throw IllegalStateException("file removal failed")
            deleted += id
        }

        fun finish(id: String, success: Boolean = true) {
            gate(id).complete(success)
        }

        fun fail(id: String) {
            gate(id).completeExceptionally(IllegalStateException("iCloud download failed"))
        }

        private fun gate(id: String) = gates.getOrPut(id) { CompletableDeferred() }
    }

    private fun queueTest(block: suspend CoroutineScope.(OriginalPrefetchQueue, FakeOriginalStore) -> Unit) =
        runBlocking {
            val queueJob = SupervisorJob(coroutineContext[Job])
            val store = FakeOriginalStore()
            val queue = OriginalPrefetchQueue(
                scope = CoroutineScope(coroutineContext + queueJob),
                maxConcurrent = 2,
                download = store::download,
                delete = store::delete,
            )
            try {
                block(queue, store)
            } finally {
                queueJob.cancel()
            }
        }

    /** runBlocking is single-threaded; yielding lets every ready coroutine run to its next suspension. */
    private suspend fun settle() = repeat(50) { yield() }

    @Test
    fun `동시에_받는_원본은_최대_2장이고_선택한_순서대로_받는다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a", "b", "c", "d"))
        settle()
        assertEquals(listOf("a", "b"), store.started)

        store.finish("a")
        settle()
        assertEquals(listOf("a", "b", "c"), store.started)
        assertEquals(2, store.maxRunning)
    }

    @Test
    fun `받는_중인_사진을_다시_미리_받아도_한_번만_받는다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a"))
        settle()
        queue.prefetch(listOf("a", "b"))
        settle()

        assertEquals(listOf("a", "b"), store.started)
    }

    @Test
    fun `저장이_받는_중인_사진을_읽으면_새로_받지_않고_끝날_때까지_기다린다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a"))
        settle()
        val waiting = async { queue.awaitPrefetch("a") }
        settle()
        assertFalse(waiting.isCompleted)

        store.finish("a")
        settle()
        assertTrue(waiting.await(), "awaitPrefetch reports that it waited for a running prefetch")
        assertEquals(listOf("a"), store.started)
    }

    @Test
    fun `기다리던_저장이_취소돼도_미리_받기는_끝까지_진행된다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a"))
        settle()
        val waiting = launch { queue.awaitPrefetch("a") }
        settle()
        waiting.cancel()
        settle()

        store.finish("a")
        settle()
        assertEquals(listOf("a"), store.completed)
    }

    @Test
    fun `미리_받지_않은_사진을_기다리면_바로_돌아온다`() = queueTest { queue, store ->
        assertFalse(queue.awaitPrefetch("never-prefetched"))
        assertTrue(store.started.isEmpty())
    }

    @Test
    fun `미리_받기가_실패해도_기다리던_저장은_예외_없이_돌아와_직접_받을_수_있다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a", "b"))
        settle()
        val waitingFailed = async { queue.awaitPrefetch("a") }
        val waitingThrown = async { queue.awaitPrefetch("b") }
        settle()

        store.finish("a", success = false)
        store.fail("b")
        settle()
        waitingFailed.await()
        waitingThrown.await()
    }

    @Test
    fun `아직_시작하지_않은_사진을_release하면_받지_않고_캐시만_지운다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a", "b", "c"))
        settle()
        val waiting = async { queue.awaitPrefetch("c") }
        queue.release(setOf("c"))
        settle()

        store.finish("a")
        store.finish("b")
        settle()
        assertEquals(listOf("a", "b"), store.started)
        assertEquals(listOf("c"), store.deleted)
        // The cancelled prefetch must not surface as an exception to a waiting save.
        waiting.await()
    }

    @Test
    fun `받는_중인_사진을_release하면_취소할_수_없어_다_받은_뒤에_지운다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a"))
        settle()
        val releasing = async { queue.release(setOf("a")) }
        settle()
        assertTrue(store.deleted.isEmpty())
        assertFalse(releasing.isCompleted)

        store.finish("a")
        settle()
        assertEquals(listOf("a"), store.completed)
        assertEquals(listOf("a"), store.deleted)
        assertTrue(releasing.isCompleted)
    }

    @Test
    fun `release한_뒤_다시_고르면_새로_미리_받는다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a", "b", "c"))
        settle()
        queue.release(setOf("c"))
        queue.prefetch(listOf("c"))
        settle()
        store.finish("a")
        settle()

        assertEquals(listOf("a", "b", "c"), store.started)
    }

    @Test
    fun `받는_중에_지우기로_한_사진을_다시_고르면_다_받은_뒤에도_지우지_않는다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a"))
        settle()
        val releasing = async { queue.release(setOf("a")) }
        settle()
        queue.prefetch(listOf("a"))
        settle()

        store.finish("a")
        settle()
        releasing.await()
        assertTrue(store.deleted.isEmpty(), "the photo was selected again, so its original stays")
        assertEquals(listOf("a"), store.started)
    }

    @Test
    fun `지우기와_미리_받기는_부른_순서대로_처리된다`() = queueTest { queue, store ->
        queue.prefetch(listOf("a", "b", "c"))
        settle()
        // Deselect c and select it again before either request has run.
        queue.releaseLater(setOf("c"))
        queue.prefetch(listOf("c"))
        settle()

        store.finish("a")
        store.finish("b")
        settle()
        assertEquals(listOf("a", "b", "c"), store.started)
        assertTrue(store.deleted.isEmpty())
    }

    @Test
    fun `한_사진을_지우다_실패해도_나머지를_지우고_다음_요청도_처리한다`() = queueTest { queue, store ->
        store.failingDeletes += "a"
        queue.release(setOf("a", "b"))
        queue.prefetch(listOf("c"))
        settle()

        assertEquals(listOf("b"), store.deleted)
        assertEquals(listOf("c"), store.started)
    }

    @Test
    fun `미리_받기_요청이_등록되기_전에_저장이_읽어도_새로_받지_않고_그_작업을_기다린다`() = queueTest { queue, store ->
        // #689 review: prefetch() only queues a command, so a save reading right after it could
        // miss the entry and start a second PhotoKit download of the same original.
        queue.prefetch(listOf("a"))
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { queue.awaitPrefetch("a") }
        settle()
        assertFalse(waiting.isCompleted, "the save waits for the queued prefetch")

        store.finish("a")
        settle()
        assertTrue(waiting.await())
        assertEquals(listOf("a"), store.started)
    }

    @Test
    fun `여러_장을_지우는_사이_먼저_끝난_사진을_다시_고르면_그_원본은_지우지_않는다`() = queueTest { queue, store ->
        // #689 review: release {A, B} while both download; A finishes, A is selected again,
        // then B finishes. The release must not delete A's original.
        queue.prefetch(listOf("a", "b"))
        settle()
        val releasing = async { queue.release(setOf("a", "b")) }
        settle()
        store.finish("a")
        settle()
        queue.prefetch(listOf("a"))
        settle()

        store.finish("b")
        settle()
        releasing.await()
        assertEquals(listOf("b"), store.deleted)
    }

    @Test
    fun `대기열이_멈추면_저장은_기다리지_않고_바로_직접_받는다`() = runBlocking {
        // A save must never hang on the queue; the app-wide scope is not expected to end, but if it does
        // every pending and later lookup completes at once.
        val queueJob = SupervisorJob(coroutineContext[Job])
        val store = FakeOriginalStore()
        val queue = OriginalPrefetchQueue(
            scope = CoroutineScope(coroutineContext + queueJob),
            download = store::download,
            delete = store::delete,
        )
        queueJob.cancel()
        settle()

        val waited = withTimeout(1_000) { queue.awaitPrefetch("a") }
        assertFalse(waited)
        withTimeout(1_000) { queue.release(setOf("a")) }
    }

    @Test
    fun `받지_않고_선택만_기록해도_그_뒤의_지우기에서_다시_고른_사진으로_본다`() = queueTest { queue, store ->
        // Low Data Mode re-selects a photo without downloading; its original must still be kept.
        queue.prefetch(listOf("a"))
        settle()
        val releasing = async { queue.release(setOf("a")) }
        settle()
        queue.markSelected(listOf("a"))
        settle()

        store.finish("a")
        settle()
        releasing.await()
        assertTrue(store.deleted.isEmpty())
        assertEquals(listOf("a"), store.started)
    }
}
