package com.mapmory.shared.presentation.triprecord.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosterCaptureTest {
    @Test
    fun exportWaitsUntilEveryPhotoIsReadyAndPosterHasDrawn() {
        val capture = PosterCapture()
        capture.photos["first"] = PosterPhotoState.Ready
        capture.photos["second"] = PosterPhotoState.Loading
        capture.drawn = true

        assertFalse(capture.canExport)
        capture.photos["second"] = PosterPhotoState.Ready
        assertTrue(capture.canExport)
        capture.drawn = false
        assertFalse(capture.canExport)
    }

    @Test
    fun failedPhotoIsDistinguishedFromLoadingAndBlocksExport() {
        val capture = PosterCapture()
        capture.drawn = true
        capture.photos["photo"] = PosterPhotoState.Loading
        assertFalse(capture.hasFailedPhotos)

        capture.photos["photo"] = PosterPhotoState.Failed
        assertTrue(capture.hasFailedPhotos)
        assertFalse(capture.canExport)
    }

    @Test
    fun retryRemountsPhotosAndWaitsForFreshSuccessfulDraw() {
        val capture = PosterCapture()
        capture.drawn = true
        capture.photos["photo"] = PosterPhotoState.Failed

        capture.retryPhotos()

        assertEquals(1, capture.retryAttempt)
        assertTrue(capture.photos.isEmpty())
        assertFalse(capture.hasFailedPhotos)
        assertFalse(capture.canExport)
        capture.photos["photo"] = PosterPhotoState.Ready
        capture.drawn = true
        assertTrue(capture.canExport)
    }
}
