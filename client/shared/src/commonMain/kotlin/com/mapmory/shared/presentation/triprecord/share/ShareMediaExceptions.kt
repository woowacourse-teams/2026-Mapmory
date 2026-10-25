package com.mapmory.shared.presentation.triprecord.share

/** A declined system permission is an expected user decision, not an app exception. */
internal class ShareMediaPermissionDeniedException(message: String) : IllegalStateException(message)

/** Only numeric platform codes cross the analytics boundary, never native error descriptions. */
internal class ShareVideoPlaybackException(
    val platformCode: Long,
    val platformExtraCode: Long? = null,
) : IllegalStateException("Video playback failed")
