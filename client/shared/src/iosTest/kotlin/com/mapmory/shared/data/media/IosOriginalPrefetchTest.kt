package com.mapmory.shared.data.media

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosOriginalPrefetchTest {
    @Test
    fun pickerCachePhotosAreNeitherPrefetchedNorReleasedByThePrefetcher() = runBlocking {
        // PHPicker fallback photos already live on the device; only the save flow may delete them.
        val bytes = IosPhotoFixtures.rotatedHeic
        val id = assertNotNull(cacheIosPickerPhoto(bytes.iosPhotoData()))
        val source = IosLocalPhotoDataSource()
        try {
            source.prefetch(listOf(id))
            source.releasePrefetched(setOf(id))
            delay(300)

            assertTrue(isIosLocalPhotoAvailable(id))
            assertContentEquals(bytes, source.read(id))
        } finally {
            source.release(id)
        }
    }
}
