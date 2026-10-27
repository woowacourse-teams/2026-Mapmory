@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IosPhotoImageIOTest {
    @Test
    fun readsOriginalExifDateFromJpegAndHeic() {
        for (bytes in listOf(IosPhotoFixtures.jpeg, IosPhotoFixtures.heic)) {
            assertEquals("2022.03.04", bytes.iosPhotoData().originalPhotoDate())
        }
    }

    @Test
    fun pendingHeicRetainsOriginalBytesAndDate() = runBlocking {
        val bytes = IosPhotoFixtures.rotatedHeic
        val id = assertNotNull(cacheIosPickerPhoto(bytes.iosPhotoData()))
        val source = IosLocalPhotoDataSource()
        try {
            assertContentEquals(bytes, source.read(id))
            assertEquals("2022.03.04", source.capturedAt(id))
            val original = assertNotNull(source.read(id)).iosPhotoData()
            // The original cache must provide full pixels, rather than the 640px picker preview.
            val fullSize = assertNotNull(original.photoDisplayJpeg()).iosPhotoData().photoDimensions()
            assertEquals(900 to 1600, fullSize)
        } finally {
            source.release(id)
        }
    }

    @Test
    fun heicWithoutEmbeddedThumbnailCreatesRotatedPickerAndRemotePreviews() {
        val bytes = IosPhotoFixtures.rotatedHeic
        val picker = assertNotNull(bytes.iosPhotoData().photoDisplayJpeg(640))
        assertEquals(360 to 640, picker.iosPhotoData().photoDimensions())
        val remote = assertNotNull(createRemotePhotoPreview(bytes))
        assertEquals(720 to 1280, remote.iosPhotoData().photoDimensions())
        assertEquals(255, remote[0].toInt() and 255)
        assertEquals(216, remote[1].toInt() and 255)
    }

    @Test
    fun missingCaptureDateDoesNotPreventPhotoPreview() {
        val withoutExif = assertNotNull(IosPhotoFixtures.jpeg.iosPhotoData().photoDisplayJpeg(640)).iosPhotoData()
        assertNull(withoutExif.originalPhotoDate())
        assertNotNull(withoutExif.photoDisplayJpeg(640))
    }

    @Test
    fun invalidAndEmptyPhotosDoNotProducePreviewOrDate() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3))) {
            val data = bytes.iosPhotoData()
            assertNull(data.originalPhotoDate())
            assertNull(data.photoDisplayJpeg(640))
            assertNull(data.photoDisplayJpeg())
        }
    }
}
