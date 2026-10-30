@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertContentEquals

class IosLocalPhotoDataSourceTest {
    @Test
    fun pickerOriginalIsRemovedAndReleaseIsIdempotent() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val id = assertNotNull(cacheIosPickerPhoto(data))
        val source = IosLocalPhotoDataSource()
        try {
            assertContentEquals(bytes, source.read(id))
            source.release(id)
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(id.removePrefix("mapmory-cache://")))
            source.release(id)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(id.removePrefix("mapmory-cache://"), null)
        }
    }
}
