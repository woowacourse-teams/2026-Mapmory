package com.mapmory.android.update

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.mapmory.android.UploadContainer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.mapmory.shared.data.settings.isPhotoRecordCompletionNotificationEnabled
import com.mapmory.shared.data.settings.isTravelVideoCompletionNotificationEnabled

@Volatile private var currentPushToken: String? = null

internal fun refreshPushToken(context: Context) {
    syncPushToken(context, currentPushToken)
    if (FirebaseApp.getApps(context).isEmpty()) return
    FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> syncPushToken(context, token) }
}

internal fun syncPushToken(context: Context, token: String?) {
    currentPushToken = token
    val application = context.applicationContext
    ensureCompletionNotificationChannels(application)
    val permissionGranted = (Build.VERSION.SDK_INT < 33 ||
        application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(application).areNotificationsEnabled()
    val manager = application.getSystemService(NotificationManager::class.java)
    fun channelEnabled(channel: String): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE
    UploadContainer.get(application).pushTokenRegistrationManager?.updateDeviceState(
        token = token,
        notificationPermissionGranted = permissionGranted,
        photoEnabled = isPhotoRecordCompletionNotificationEnabled(application) && channelEnabled(PhotoRecordNotificationChannelId),
        videoEnabled = isTravelVideoCompletionNotificationEnabled(application) && channelEnabled(TravelVideoNotificationChannelId),
    )
}

internal suspend fun refreshPushTokenAndAwaitRegistration(context: Context) {
    syncPushToken(context, currentPushToken)
    if (FirebaseApp.getApps(context).isNotEmpty()) {
        val token = suspendCancellableCoroutine<String?> { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
        syncPushToken(context, token ?: currentPushToken)
    }
    UploadContainer.get(context).pushTokenRegistrationManager?.videoPushTokenId()
}
