package com.mapmory.shared.presentation.components

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhotoViewerOriginalCacheTest {
    @Test
    fun `browsing more than three photos preloads neighbours and reuses their original bytes`() = runBlocking {
        val cache = PhotoViewerOriginalCache()
        val ids = (0..7).map(Int::toString)
        val loads = mutableListOf<String>()
        val bytes = ids.associateWith { byteArrayOf(it.toByte()) }
        suspend fun load(id: String): ByteArray? { loads += id; return bytes[id] }

        cache.prepare(ids, 0, ::load)
        assertEquals(listOf("0", "1"), loads)
        for (page in 1..5) {
            val prefetched = cache[ids[page]]
            cache.prepare(ids, page, ::load)
            assertSame(prefetched, cache[ids[page]], "Moving to a prefetched page must reuse its original")
            assertTrue(cache.contains(ids[page + 1]))
            assertEquals(3, ids.count(cache::contains))
            assertFalse(cache.contains(ids.getOrElse(page - 2) { "missing" }))
        }
        assertEquals((0..6).map(Int::toString), loads)
        // Returning to the immediately previous photo does not reload that photo.
        val previous = cache["4"]
        cache.prepare(ids, 4, ::load)
        assertSame(previous, cache["4"])
        assertEquals(1, loads.count { it == "4" })
    }

    @Test
    fun `failed local lookup is remembered while its page remains nearby`() = runBlocking {
        val cache = PhotoViewerOriginalCache()
        var count = 0
        cache.prepare(listOf("a", "b"), 0) { count++; null }
        cache.prepare(listOf("a", "b"), 1) { count++; null }
        assertEquals(2, count)
        assertTrue(cache.contains("a"))
        assertEquals(null, cache["a"])
    }

    @Test
    fun `changing page cancels pending preload without retaining stale bytes`() = runBlocking {
        val cache = PhotoViewerOriginalCache()
        val started = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<ByteArray?>()
        val pending = launch {
            cache.prepare(listOf("a", "b", "c", "d"), 0) {
                started.complete(Unit)
                blocked.await()
            }
        }
        started.await()
        pending.cancel()
        pending.join()
        cache.prepare(listOf("a", "b", "c", "d"), 3) { byteArrayOf(1) }
        blocked.complete(byteArrayOf(2))
        assertFalse(cache.contains("a"))
        assertFalse(cache.contains("b"))
        assertTrue(cache.contains("c"))
        assertTrue(cache.contains("d"))
    }
}
