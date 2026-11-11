package com.mapmory.shared.data.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OriginalPrefetchSessionTest {
    private class RecordingPrefetcher : OriginalPhotoPrefetcher {
        val prefetched = mutableListOf<List<String>>()
        val released = mutableListOf<Set<String>>()

        override fun prefetch(localIds: List<String>) {
            prefetched += localIds
        }

        override fun releasePrefetched(localIds: Set<String>) {
            released += localIds
        }
    }

    @Test
    fun `고른_사진을_고른_순서대로_미리_받는다`() {
        val prefetcher = RecordingPrefetcher()
        val session = OriginalPrefetchSession(prefetcher) { emptySet() }

        session.select(listOf("cover", "b", "c"))

        assertEquals(listOf(listOf("cover", "b", "c")), prefetcher.prefetched)
        assertTrue(prefetcher.released.isEmpty())
    }

    @Test
    fun `다시_고르면_선택에서_빠진_사진의_원본만_지운다`() {
        val prefetcher = RecordingPrefetcher()
        val session = OriginalPrefetchSession(prefetcher) { emptySet() }

        session.select(listOf("a", "b", "c"))
        session.select(listOf("b", "d"))

        assertEquals(listOf(setOf("a", "c")), prefetcher.released)
        assertEquals(listOf("b", "d"), prefetcher.prefetched.last())
    }

    @Test
    fun `작성을_그만두면_미리_받은_원본을_모두_지운다`() {
        val prefetcher = RecordingPrefetcher()
        val session = OriginalPrefetchSession(prefetcher) { emptySet() }

        session.select(listOf("a", "b"))
        session.close()

        assertEquals(listOf(setOf("a", "b")), prefetcher.released)
    }

    @Test
    fun `백그라운드_저장을_기다리는_사진의_원본은_지우지_않는다`() {
        // 저장 대기열이 곧 이 원본을 읽는다. 지우면 iCloud에서 다시 받아야 한다.
        val prefetcher = RecordingPrefetcher()
        var pending = emptySet<String>()
        val session = OriginalPrefetchSession(prefetcher) { pending }

        session.select(listOf("a", "b", "c"))
        pending = setOf("a")
        session.select(listOf("c"))
        pending = setOf("a", "c")
        session.close()

        assertEquals(listOf(setOf("b")), prefetcher.released)
    }

    @Test
    fun `지울_사진이_없으면_release를_부르지_않는다`() {
        val prefetcher = RecordingPrefetcher()
        val session = OriginalPrefetchSession(prefetcher) { emptySet() }

        session.select(listOf("a"))
        session.select(listOf("a"))
        val empty = OriginalPrefetchSession(prefetcher) { emptySet() }
        empty.close()

        assertTrue(prefetcher.released.isEmpty())
    }

    @Test
    fun `닫은_뒤에는_정리를_다시_하지_않는다`() {
        val prefetcher = RecordingPrefetcher()
        val session = OriginalPrefetchSession(prefetcher) { emptySet() }

        session.select(listOf("a"))
        session.close()
        session.close()

        assertEquals(listOf(setOf("a")), prefetcher.released)
    }
}
