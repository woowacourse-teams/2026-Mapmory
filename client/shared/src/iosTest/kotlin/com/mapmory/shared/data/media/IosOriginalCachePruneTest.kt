@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.writeToFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosOriginalCachePruneTest {
    @Test
    fun startupPruneKeepsOnlyOriginalsOfPendingSaves() = runBlocking {
        // An app killed mid-flow never reaches onCleared; its prefetched originals are removed on next launch.
        val directory = assertNotNull(iosOriginalCacheDirectory())
        val keptId = "prune-test-kept/L0/001"
        val abandonedId = "prune-test-abandoned/L0/001"
        val kept = "$directory/${iosOriginalCacheFileName(keptId, version = 1.0)}"
        val abandoned = "$directory/${iosOriginalCacheFileName(abandonedId, version = 2.0)}"
        val interrupted = "$directory/prune-test-interrupted.part"
        val downloading = "$directory/prune-test-downloading.part"
        val manager = NSFileManager.defaultManager
        val files = listOf(kept, abandoned, interrupted, downloading)
        for (path in files) {
            assertTrue(byteArrayOf(1, 2, 3).iosPhotoData().writeToFile(path, atomically = true))
        }
        // Left by a download the killed app never finished.
        manager.setAttributes(
            mapOf(NSFileModificationDate to NSDate.dateWithTimeIntervalSinceNow(-60.0 * 60.0)),
            ofItemAtPath = interrupted,
            error = null,
        )
        try {
            pruneIosOriginalCache(keep = setOf(keptId))

            assertTrue(manager.fileExistsAtPath(kept), "a pending save still needs this original")
            assertFalse(manager.fileExistsAtPath(abandoned))
            assertFalse(manager.fileExistsAtPath(interrupted))
            // A restored save may be writing this right now; deleting it would fail that save.
            assertTrue(manager.fileExistsAtPath(downloading))
        } finally {
            files.forEach { manager.removeItemAtPath(it, null) }
        }
    }
}
