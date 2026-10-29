package com.mapmory.shared.data.media

import com.mapmory.shared.presentation.photo.IosPhotoLibraryController
import com.mapmory.shared.presentation.photo.SelectedPhoto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IosFullResolutionPreviewTest {
    @Test
    fun pickerFallbackLoadsCachedOriginalInsteadOfReturningNullOr640PixelPreview() = runBlocking {
        val bytes = IosPhotoFixtures.rotatedHeic
        val id = assertNotNull(cacheIosPickerPhoto(bytes.iosPhotoData()))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = IosPhotoLibraryController(scope)
        try {
            val original = withTimeout(10_000) {
                suspendCancellableCoroutine { continuation ->
                    controller.loadFullResolutionPreview(
                        SelectedPhoto(id, "photo", bytes.iosPhotoData().photoDisplayJpeg(640)),
                    ) { continuation.resume(it) }
                }
            }
            assertContentEquals(bytes, original)
        } finally {
            controller.cancelFullResolutionPreview()
            scope.cancel()
            IosLocalPhotoDataSource().release(id)
        }
    }

    @Test
    fun cachedReadFailureCompletesWithNull() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = IosPhotoLibraryController(scope, LocalPhotoDataSource { error("read failed") })
        try {
            val bytes = withTimeout(10_000) {
                suspendCancellableCoroutine { continuation ->
                    controller.loadFullResolutionPreview(SelectedPhoto("mapmory-cache://missing", "photo", null)) {
                        continuation.resume(it)
                    }
                }
            }
            assertNull(bytes)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun closingViewerCancelsPendingOriginalReadWithoutPublishingAFrame() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val waiting = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = IosPhotoLibraryController(scope, LocalPhotoDataSource {
            started.complete(Unit)
            try {
                waiting.await()
                IosPhotoFixtures.heic
            } finally {
                finished.complete(Unit)
            }
        })
        var delivered = false
        try {
            controller.loadFullResolutionPreview(SelectedPhoto("mapmory-cache://pending", "photo", null)) {
                delivered = true
            }
            withTimeout(10_000) { started.await() }
            controller.cancelFullResolutionPreview()
            withTimeout(10_000) { finished.await() }
            assertFalse(delivered)
        } finally {
            scope.cancel()
        }
    }
}
