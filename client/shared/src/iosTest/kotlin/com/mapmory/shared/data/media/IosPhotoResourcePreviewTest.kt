@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class IosPhotoResourcePreviewTest {
    private val files = NSFileManager.defaultManager

    @Test
    fun previewUsesTemporaryOriginalAndKeepsOnlyThe640PixelResult() = runBlocking {
        var originalPath: String? = null
        val preview = withIosTemporaryPhotoFile(
            write = { path, complete ->
                originalPath = path
                complete(IosPhotoFixtures.rotatedHeic.iosPhotoData().writeToFile(path, true))
            },
            read = { path ->
                assertTrue(files.fileExistsAtPath(path))
                iosPhotoFileDisplayJpeg(path, 640)
            },
        )
        assertEquals(360 to 640, assertNotNull(preview).iosPhotoData().photoDimensions())
        assertFalse(files.fileExistsAtPath(assertNotNull(originalPath)))
    }

    @Test
    fun partialDownloadAndDecodeFailureBothDeleteTheOriginal() = runBlocking {
        for (downloadSucceeded in listOf(false, true)) {
            var originalPath: String? = null
            val preview = withIosTemporaryPhotoFile(
                write = { path, complete ->
                    originalPath = path
                    assertTrue(byteArrayOf(1, 2, 3).iosPhotoData().writeToFile(path, true))
                    complete(downloadSucceeded)
                },
                read = { path ->
                    assertTrue(downloadSucceeded, "Do not decode a failed download")
                    iosPhotoFileDisplayJpeg(path, 640)
                },
            )
            assertNull(preview)
            assertFalse(files.fileExistsAtPath(assertNotNull(originalPath)))
        }
    }

    @Test
    fun decodeExceptionStillDeletesTheOriginal() = runBlocking {
        var originalPath: String? = null
        assertFailsWith<IllegalStateException> {
            withIosTemporaryPhotoFile(
                write = { path, complete ->
                    originalPath = path
                    complete(IosPhotoFixtures.heic.iosPhotoData().writeToFile(path, true))
                },
                read = { error("decode failed") },
            )
        }
        assertFalse(files.fileExistsAtPath(assertNotNull(originalPath)))
    }

    @Test
    fun cancelledRequestWaitsForWriterThenDeletesWithoutDecoding() = runBlocking {
        val pending = CompletableDeferred<Pair<String, (Boolean) -> Unit>>()
        var decoded = false
        val job = launch(Dispatchers.Default) {
            withIosTemporaryPhotoFile(
                write = { path, complete -> pending.complete(path to complete) },
                read = { decoded = true },
            )
        }
        val (path, complete) = withTimeout(10_000) { pending.await() }
        // PhotoKit's writer is still active and may create its destination after cancellation.
        job.cancel()
        assertFalse(job.isCompleted)
        assertTrue(IosPhotoFixtures.heic.iosPhotoData().writeToFile(path, true))
        complete(true)
        withTimeout(10_000) { job.join() }
        assertFalse(decoded)
        assertFalse(files.fileExistsAtPath(path))
    }

    @Test
    fun concurrentPreviewsNeverDeleteAnotherRequestOrAnUploadOriginal() = runBlocking {
        val cacheRoot = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        val uploadDirectory = "$cacheRoot/mapmory-upload-originals"
        assertTrue(files.createDirectoryAtPath(uploadDirectory, true, null, null))
        val uploadPath = "$uploadDirectory/test-${NSUUID().UUIDString}"
        val original = IosPhotoFixtures.heic
        assertTrue(original.iosPhotoData().writeToFile(uploadPath, true))
        val firstReading = CompletableDeferred<String>()
        val secondReading = CompletableDeferred<String>()
        try {
            withTimeout(10_000) {
                listOf(firstReading to secondReading, secondReading to firstReading).map { (own, other) ->
                    async(Dispatchers.Default) {
                        withIosTemporaryPhotoFile(
                            write = { path, complete -> complete(original.iosPhotoData().writeToFile(path, true)) },
                            read = { path ->
                                own.complete(path)
                                val otherPath = other.await()
                                assertNotEquals(path, otherPath)
                                assertTrue(files.fileExistsAtPath(path))
                                iosPhotoFileDisplayJpeg(path, 640)
                            },
                        )
                    }
                }.awaitAll().forEach { assertNotNull(it) }
            }
            assertFalse(files.fileExistsAtPath(firstReading.await()))
            assertFalse(files.fileExistsAtPath(secondReading.await()))
            assertContentEquals(original, assertNotNull(NSData.dataWithContentsOfFile(uploadPath)).photoBytes())
        } finally {
            files.removeItemAtPath(uploadPath, null)
        }
    }
}
