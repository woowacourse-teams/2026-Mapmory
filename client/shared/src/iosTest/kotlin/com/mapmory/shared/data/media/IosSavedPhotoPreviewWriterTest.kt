@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.coroutines.runBlocking
import platform.Foundation.writeToFile
import platform.Foundation.NSUUID
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSFileManager
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

    @Test
    fun aLocalKeyWriteThatDidNotLandIsNotReportedAsReplaced() = runBlocking {
        // The save later copies the local key onto the objectKey; if only the objectKey holds 1080px,
        // that copy would bring the small preview back, so this must not count as a replacement.
        val cache = object : PhotoPreviewCache {
            val entries = mutableMapOf<String, ByteArray>()
            override suspend fun read(objectKey: String): ByteArray? = entries[objectKey]
            override suspend fun write(objectKey: String, bytes: ByteArray) {
                if (objectKey != "local-id") entries[objectKey] = bytes
            }
        }

        assertFalse(IosSavedPhotoPreviewWriter(cache).write("local-id", "travel-records/1/a.jpg", IosPhotoFixtures.rotatedHeic))
    }

    @Test
    fun aStagedUploadFileRebuildsThePreviewUnderBothKeysAt1080Pixels() = runBlocking {
        // #693: a completed transfer has no bytes in memory, only its staged upload file.
        val path = "${NSTemporaryDirectory()}${NSUUID().UUIDString}.upload"
        assertTrue(IosPhotoFixtures.rotatedHeic.iosPhotoData().writeToFile(path, atomically = true))
        val cache = MemoryPreviewCache().apply { entries["local-id"] = byteArrayOf(1) }
        try {
            assertTrue(IosSavedPhotoPreviewWriter(cache).writeFromFile("local-id", "travel-records/1/a.jpg", path))

            val local = assertNotNull(cache.entries["local-id"])
            assertContentEquals(local, cache.entries["travel-records/1/a.jpg"])
            val (width, height) = assertNotNull(local.iosPhotoData().photoDimensions())
            assertEquals(1080, maxOf(width, height))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }

    @Test
    fun aMissingStagedFileKeepsTheExistingPreview() = runBlocking {
        val cache = MemoryPreviewCache().apply { entries["local-id"] = byteArrayOf(1) }

        assertFalse(IosSavedPhotoPreviewWriter(cache).writeFromFile("local-id", "travel-records/1/a.jpg", "/missing.upload"))

        assertEquals(setOf("local-id"), cache.entries.keys)
    }
}
