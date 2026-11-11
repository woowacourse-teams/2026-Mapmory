package com.mapmory.shared.presentation.photo

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoImageResultTest {
    @Test
    fun degradedResultWaitsForTheFinalImageEvenWhenAnImageIsPresent() {
        assertEquals(PhotoImageResult.WAIT, classifyPhotoImageResult(isDegraded = true, isCancelled = false, hasImage = true))
        assertEquals(PhotoImageResult.WAIT, classifyPhotoImageResult(isDegraded = true, isCancelled = false, hasImage = false))
    }

    @Test
    fun finalImageIsDelivered() {
        assertEquals(PhotoImageResult.DELIVER, classifyPhotoImageResult(isDegraded = false, isCancelled = false, hasImage = true))
    }

    @Test
    fun cancelledOrMissingFinalImageFails() {
        assertEquals(PhotoImageResult.FAIL, classifyPhotoImageResult(isDegraded = false, isCancelled = true, hasImage = false))
        assertEquals(PhotoImageResult.FAIL, classifyPhotoImageResult(isDegraded = true, isCancelled = true, hasImage = true))
        assertEquals(PhotoImageResult.FAIL, classifyPhotoImageResult(isDegraded = false, isCancelled = false, hasImage = false))
    }

    // List previews run with networkAccessAllowed = false: an iCloud-optimized photo sends its local
    // degraded thumbnail first, then a final call without an image or marked PHImageResultIsInCloudKey.

    @Test
    fun heldDegradedThumbnailCompletesWhenTheFinalImageIsMissing() {
        assertEquals(
            PhotoImageResult.DELIVER_DEGRADED,
            classifyPhotoImageResult(isDegraded = false, isCancelled = false, hasImage = false, hasHeldDegraded = true),
        )
    }

    @Test
    fun heldDegradedThumbnailCompletesWhenTheFinalResultIsInICloud() {
        assertEquals(
            PhotoImageResult.DELIVER_DEGRADED,
            classifyPhotoImageResult(
                isDegraded = false, isCancelled = false, hasImage = false, isInCloud = true, hasHeldDegraded = true,
            ),
        )
        assertEquals(
            PhotoImageResult.DELIVER_DEGRADED,
            classifyPhotoImageResult(
                isDegraded = false, isCancelled = false, hasImage = true, isInCloud = true, hasHeldDegraded = true,
            ),
        )
    }

    @Test
    fun aLocalFinalImageStillWinsOverTheHeldDegradedThumbnail() {
        assertEquals(
            PhotoImageResult.DELIVER,
            classifyPhotoImageResult(isDegraded = false, isCancelled = false, hasImage = true, hasHeldDegraded = true),
        )
    }

    @Test
    fun degradedResultsKeepWaitingEvenWhenOneIsAlreadyHeld() {
        assertEquals(
            PhotoImageResult.WAIT,
            classifyPhotoImageResult(isDegraded = true, isCancelled = false, hasImage = true, hasHeldDegraded = true),
        )
    }

    @Test
    fun cancellationAfterADegradedThumbnailCompletesWithIt() {
        assertEquals(
            PhotoImageResult.DELIVER_DEGRADED,
            classifyPhotoImageResult(isDegraded = false, isCancelled = true, hasImage = false, hasHeldDegraded = true),
        )
    }

    @Test
    fun anICloudFinalResultWithoutAHeldThumbnailFails() {
        assertEquals(
            PhotoImageResult.FAIL,
            classifyPhotoImageResult(isDegraded = false, isCancelled = false, hasImage = false, isInCloud = true),
        )
    }
}
