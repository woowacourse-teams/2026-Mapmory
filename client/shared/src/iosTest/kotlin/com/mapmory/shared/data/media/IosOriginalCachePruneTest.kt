@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
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
        val partial = "$directory/prune-test-interrupted.part"
        val manager = NSFileManager.defaultManager
        for (path in listOf(kept, abandoned, partial)) {
            assertTrue(byteArrayOf(1, 2, 3).iosPhotoData().writeToFile(path, atomically = true))
        }
        try {
            pruneIosOriginalCache(keep = setOf(keptId))

            assertTrue(manager.fileExistsAtPath(kept), "a pending save still needs this original")
            assertFalse(manager.fileExistsAtPath(abandoned))
            assertFalse(manager.fileExistsAtPath(partial))
        } finally {
            listOf(kept, abandoned, partial).forEach { manager.removeItemAtPath(it, null) }
        }
    }
}
