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
}
