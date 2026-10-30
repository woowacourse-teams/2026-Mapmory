package com.mapmory.shared.data.media

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordedPhotoIndexTest {
    @Test
    fun `다른_기록이_사용하는_사진은_삭제해도_제외를_유지한다`() = runBlocking {
        val index = RecordedPhotoIndex(MemoryPhotoPreviewCache())
        index.replace(1, setOf("a", "b"))
        index.replace(2, setOf("b", "c"))
        index.replace(1, emptySet())
        assertEquals(setOf("b", "c"), index.ids.value)
        index.replace(2, setOf("c"))
        assertEquals(setOf("c"), index.ids.value)
    }

    @Test
    fun `새_인스턴스에서도_천장의_사용이력을_사진바이트_없이_복원한다`() = runBlocking {
        val storage = MemoryPhotoPreviewCache()
        val first = RecordedPhotoIndex(storage)
        repeat(10) { record ->
            first.replace(record.toLong(), (0 until 100).map { "photo-${record * 100 + it}" }.toSet())
        }
        val restored = RecordedPhotoIndex(storage)
        restored.initialize()
        assertEquals(1000, restored.ids.value.size)
        assertEquals(first.ids.value, restored.ids.value)
    }
}
