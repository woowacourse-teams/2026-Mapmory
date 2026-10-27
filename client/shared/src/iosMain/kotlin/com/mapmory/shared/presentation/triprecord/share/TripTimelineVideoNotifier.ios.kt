@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.presentation.triprecord.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

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

    override fun showProgress(jobId: Long) {
        clearProgress(jobId)
        val content = UNMutableNotificationContent().apply {
            setTitle("여행 영상 생성 중")
            setBody("앱 화면을 닫아도 생성은 계속돼요. 앱을 다시 열면 완료 여부를 확인해요.")
        }
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(progressIdentifier(jobId), content, null),
        ) { _ -> }
    }

    override fun clearProgress(jobId: Long) {
        val identifier = progressIdentifier(jobId)
        center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
    }

    override suspend fun showReady(jobId: Long): Boolean = suspendCancellableCoroutine { continuation ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val authorized = settings?.authorizationStatus == UNAuthorizationStatusAuthorized ||
                settings?.authorizationStatus == UNAuthorizationStatusProvisional
            if (!authorized) {
                continuation.resume(false)
                return@getNotificationSettingsWithCompletionHandler
            }

            clearProgress(jobId)
            val content = UNMutableNotificationContent().apply {
                setTitle("여행 영상이 준비됐어요")
                setBody("앱에서 미리 보고 저장하거나 공유해 보세요.")
                setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
            }
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier("share-video-$jobId", content, null),
            ) { error ->
                if (continuation.isActive) continuation.resume(error == null)
            }
        }
    }

    private fun progressIdentifier(jobId: Long) = "share-video-progress-$jobId"
}
