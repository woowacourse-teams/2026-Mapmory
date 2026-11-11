package com.mapmory.shared.presentation.photo

/** What to do with one `PHImageManager.requestImage` result-handler call. */
internal enum class PhotoImageResult {
    /** A temporary low-quality image. Photos calls the handler again unless the request is cancelled. */
    WAIT,

    /** The final image for this request. */
    DELIVER,

    /** No usable final image; complete with the degraded image held from an earlier call. */
    DELIVER_DEGRADED,

    /** No final image will arrive; fall back instead of waiting. */
    FAIL,
}

/**
 * Opportunistic requests may call the handler several times, the first time possibly synchronously
 * on the calling thread. Only a non-degraded image is final (`PHImageResultIsDegradedKey`), and a
 * cancelled request may stop without a final call, so cancellation never waits.
 *
 * Without network access the final call for an iCloud-only photo carries no image or is marked
 * `PHImageResultIsInCloudKey` (a new request with network access would be needed). The caller then
 * completes with the local degraded thumbnail it held ([hasHeldDegraded]) instead of failing.
 */
internal fun classifyPhotoImageResult(
    isDegraded: Boolean,
    isCancelled: Boolean,
    hasImage: Boolean,
    isInCloud: Boolean = false,
    hasHeldDegraded: Boolean = false,
): PhotoImageResult = when {
    isCancelled -> if (hasHeldDegraded) PhotoImageResult.DELIVER_DEGRADED else PhotoImageResult.FAIL
    isDegraded -> PhotoImageResult.WAIT
    hasImage && !isInCloud -> PhotoImageResult.DELIVER
    hasHeldDegraded -> PhotoImageResult.DELIVER_DEGRADED
    else -> PhotoImageResult.FAIL
}
