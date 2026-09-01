package com.mapmory.shared.data.media

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PhotoPreviewLoaderTest {
    @Test
    fun cacheUsesObjectKeyInsteadOfExpiringPresignedUrl() = runBlocking {
        var downloadCount = 0
        val loader = PhotoPreviewLoader(
            cache = MemoryPhotoPreviewCache(),
            remoteSource = PhotoRemoteSource {
                downloadCount += 1
                Result.success(byteArrayOf(0x01, 0x02, 0x03))
            },
        )

        val first = loader.load(
            objectKey = "travel-records/10/photo.jpg",
            presignedGetUrl = "https://bucket.example.com/photo.jpg?signature=first",
        ).getOrThrow()
        val second = loader.load(
            objectKey = "travel-records/10/photo.jpg",
            presignedGetUrl = "https://bucket.example.com/photo.jpg?signature=rotated",
        ).getOrThrow()

        assertContentEquals(first, second)
        assertEquals(1, downloadCount)
    }

    @Test
    fun memoryCacheReturnsDefensiveCopies() = runBlocking {
        val cache = MemoryPhotoPreviewCache()
        val original = byteArrayOf(0x01, 0x02)
        cache.write("object-key", original)
        original[0] = 0x0A

        val cached = requireNotNull(cache.read("object-key"))
        cached[1] = 0x0B

        assertContentEquals(byteArrayOf(0x01, 0x02), cache.read("object-key"))
        assertFalse(objectKeyCacheFileName("a") == objectKeyCacheFileName("b"))
    }
}
