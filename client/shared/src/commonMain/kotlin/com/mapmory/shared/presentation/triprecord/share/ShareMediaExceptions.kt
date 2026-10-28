package com.mapmory.shared.presentation.triprecord.share

/** A declined system permission is an expected user decision, not an app exception. */
internal class ShareMediaPermissionDeniedException(message: String) : IllegalStateException(message)

/**
 * Native player failure; classified as media_playback, not client_logic, by analytics.
 * Only numeric platform codes cross the analytics boundary, never native error descriptions.
 */
internal class ShareVideoPlaybackException(
    val platformCode: Long,
    val platformExtraCode: Long? = null,
) : IllegalStateException("Video playback failed")
