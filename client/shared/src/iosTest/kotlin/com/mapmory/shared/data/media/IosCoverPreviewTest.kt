package com.mapmory.shared.data.media

import com.mapmory.shared.presentation.photo.IosPhotoLibraryController
import com.mapmory.shared.presentation.photo.SelectedPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class IosCoverPreviewTest {
    @Test
    fun cachedPickerPhotoProducesA1080PixelCoverPreview() = runBlocking {
        val bytes = IosPhotoFixtures.rotatedHeic
        val id = assertNotNull(cacheIosPickerPhoto(bytes.iosPhotoData()))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = IosPhotoLibraryController(scope)
        try {
            val preview = withTimeout(10_000) {
                suspendCancellableCoroutine { continuation ->
                    controller.loadCoverPreview(SelectedPhoto(id, "photo", null)) { continuation.resume(it) }
                }
            }
            val (width, height) = assertNotNull(assertNotNull(preview).iosPhotoData().photoDimensions())
            // The 900x1600 fixture is larger than the cover preview, so its long side is clamped to 1080px.
            assertEquals(1080, maxOf(width, height))
            assertEquals(255, preview[0].toInt() and 255)
            assertEquals(216, preview[1].toInt() and 255)
        } finally {
            controller.cancelCoverPreview()
            scope.cancel()
            IosLocalPhotoDataSource().release(id)
        }
    }

    @Test
    fun cancelledCoverPreviewNeverCompletes() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = IosPhotoLibraryController(
            scope,
            LocalPhotoDataSource { delay(300); IosPhotoFixtures.rotatedHeic },
        )
        var completed = false
        try {
            controller.loadCoverPreview(SelectedPhoto("mapmory-cache://slow", "photo", null)) { completed = true }
            controller.cancelCoverPreview()
            delay(800)
            assertFalse(completed)
        } finally {
            scope.cancel()
        }
    }
}
