package com.mapmory.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MapmoryNotificationIntent {
    const val ACTION_OPEN_RECORD = "com.mapmory.shared.OPEN_RECORD_NOTIFICATION"
    const val ACTION_OPEN_VIDEO = "com.mapmory.shared.OPEN_VIDEO_NOTIFICATION"
    const val EXTRA_RECORD_ID = "com.mapmory.shared.extra.RECORD_ID"
    const val EXTRA_VIDEO_JOB_ID = "com.mapmory.shared.extra.VIDEO_JOB_ID"
}

/** Allows a platform entry point to delegate system back presses to the shared NavController. */
class MapmoryNavigation {
    private var backHandler: (() -> Boolean)? = null
    private val mutableNotificationRequest = MutableStateFlow<NotificationNavigationRequest?>(null)
    internal val notificationRequest: StateFlow<NotificationNavigationRequest?> =
        mutableNotificationRequest.asStateFlow()
    private var nextNotificationRequestId = 0L

    internal fun bindBackHandler(handler: () -> Boolean) {
        backHandler = handler
    }

    internal fun unbindBackHandler() {
        backHandler = null
    }

    fun popBackStack(): Boolean = backHandler?.invoke() == true

    fun openRecordNotification(recordId: Long, preserveBackStack: Boolean) {
        if (recordId <= 0L) return
        mutableNotificationRequest.value = NotificationNavigationRequest(
            id = ++nextNotificationRequestId,
            target = NotificationNavigationTarget.Record(recordId),
            preserveBackStack = preserveBackStack,
        )
    }

    fun openVideoNotification(jobId: Long, preserveBackStack: Boolean) {
        if (jobId <= 0L) return
        mutableNotificationRequest.value = NotificationNavigationRequest(
            id = ++nextNotificationRequestId,
            target = NotificationNavigationTarget.Video(jobId),
            preserveBackStack = preserveBackStack,
        )
    }

    internal fun awaitNotificationLogin(requestId: Long, returnRoute: Any) {
        mutableNotificationRequest.value?.takeIf { it.id == requestId }?.let { request ->
            mutableNotificationRequest.value = request.copy(awaitingLogin = true, returnRoute = returnRoute)
        }
    }

    internal fun consumeNotification(requestId: Long) {
        if (mutableNotificationRequest.value?.id == requestId) mutableNotificationRequest.value = null
    }
}

internal data class NotificationNavigationRequest(
    val id: Long,
    val target: NotificationNavigationTarget,
    val preserveBackStack: Boolean,
    val awaitingLogin: Boolean = false,
    val returnRoute: Any? = null,
)

internal sealed interface NotificationNavigationTarget {
    data class Record(val id: Long) : NotificationNavigationTarget
    data class Video(val jobId: Long) : NotificationNavigationTarget
}
