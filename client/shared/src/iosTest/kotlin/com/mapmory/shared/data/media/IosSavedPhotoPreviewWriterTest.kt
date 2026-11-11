package com.mapmory.shared.data.media

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosSavedPhotoPreviewWriterTest {
    private class MemoryPreviewCache : PhotoPreviewCache {
        val entries = mutableMapOf<String, ByteArray>()
        override suspend fun read(objectKey: String): ByteArray? = entries[objectKey]
        override suspend fun write(objectKey: String, bytes: ByteArray) {
            entries[objectKey] = bytes
        }
    }

    @Test
    fun uploadedOriginalReplacesTheSmallPreviewUnderBothKeysAt1080Pixels() = runBlocking {
        val cache = MemoryPreviewCache().apply { entries["local-id"] = byteArrayOf(1) }
        val writer = IosSavedPhotoPreviewWriter(cache)

        assertTrue(writer.write("local-id", "travel-records/1/a.jpg", IosPhotoFixtures.rotatedHeic))

        val local = assertNotNull(cache.entries["local-id"])
        assertContentEquals(local, cache.entries["travel-records/1/a.jpg"])
        // The 900x1600 fixture is larger than the target, so its long side is clamped to 1080px.
        val (width, height) = assertNotNull(local.iosPhotoData().photoDimensions())
        assertEquals(1080, maxOf(width, height))
    }

    @Test
    fun unreadableOriginalKeepsTheExistingPreview() = runBlocking {
        val cache = MemoryPreviewCache().apply { entries["local-id"] = byteArrayOf(1) }
        val writer = IosSavedPhotoPreviewWriter(cache)

        assertFalse(writer.write("local-id", "travel-records/1/a.jpg", byteArrayOf(1, 2, 3)))

        assertContentEquals(byteArrayOf(1), cache.entries["local-id"])
        assertEquals(setOf("local-id"), cache.entries.keys)
    }

    @Test
    fun aCacheWriteThatDidNotLandIsNotReportedAsReplaced() = runBlocking {
        // IosPhotoPreviewCache.write returns silently on failure; the caller must then keep its own preview.
        val cache = object : PhotoPreviewCache {
            override suspend fun read(objectKey: String): ByteArray? = null
            override suspend fun write(objectKey: String, bytes: ByteArray) = Unit
        }

        assertFalse(IosSavedPhotoPreviewWriter(cache).write("local-id", "travel-records/1/a.jpg", IosPhotoFixtures.rotatedHeic))
    }
}
