package com.mapmory.android.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mapmory.android.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal const val AppUpdateNotificationChannelId = "app-updates"

internal fun ensureAppUpdateNotificationChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                AppUpdateNotificationChannelId,
                "앱 업데이트",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}

class MapmoryFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        ensureAppUpdateNotificationChannel(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(AppUpdateNotificationChannelId)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                message.messageId?.hashCode() ?: 0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(this, AppUpdateNotificationChannelId)
            .setSmallIcon(R.drawable.ic_notification_pin)
            .setContentTitle(notification.title ?: "Mapmory 업데이트")
            .setContentText(notification.body ?: "새 버전이 준비됐어요.")
            .setAutoCancel(true)
        if (pendingIntent != null) builder.setContentIntent(pendingIntent)
        manager.notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), builder.build())
    }
}
