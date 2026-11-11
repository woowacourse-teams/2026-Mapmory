package com.mapmory.shared.presentation.photo

/** What to do with one `PHImageManager.requestImage` result-handler call. */
internal enum class PhotoImageResult {
    /** A temporary low-quality image. Photos calls the handler again unless the request is cancelled. */
    WAIT,

    /** The final image for this request. */
    DELIVER,

    /** No final image will arrive; fall back instead of waiting. */
    FAIL,
}

/**
 * Opportunistic requests may call the handler several times, the first time possibly synchronously
 * on the calling thread. Only a non-degraded image is final (`PHImageResultIsDegradedKey`), and a
 * cancelled request may stop without a final call, so cancellation always fails immediately.
 */
internal fun classifyPhotoImageResult(
    isDegraded: Boolean,
    isCancelled: Boolean,
    hasImage: Boolean,
): PhotoImageResult = when {
    isCancelled -> PhotoImageResult.FAIL
    isDegraded -> PhotoImageResult.WAIT
    hasImage -> PhotoImageResult.DELIVER
    else -> PhotoImageResult.FAIL
}
