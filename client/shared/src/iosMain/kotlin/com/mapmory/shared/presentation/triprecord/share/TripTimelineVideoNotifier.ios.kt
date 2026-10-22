@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

@Composable
internal actual fun rememberTripTimelineVideoNotifier(): TripTimelineVideoNotifier =
    remember { IosTripTimelineVideoNotifier() }

private class IosTripTimelineVideoNotifier : TripTimelineVideoNotifier {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override fun requestPermissionIfNeeded() {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }

    override fun showReady(jobId: Long) {
        val content = UNMutableNotificationContent().apply {
            setTitle("여행 영상이 준비됐어요")
            setBody("앱에서 미리 보고 저장하거나 공유해 보세요.")
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
        }
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier("share-video-$jobId", content, null),
        ) { _ -> }
    }
}
