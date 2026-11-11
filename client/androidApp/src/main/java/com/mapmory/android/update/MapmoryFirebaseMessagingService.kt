package com.mapmory.android.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mapmory.android.MainActivity
import com.mapmory.android.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mapmory.android.isMapmoryAppInForeground
import com.mapmory.shared.MapmoryNotificationIntent
import com.mapmory.shared.MapmoryNotificationTarget
import com.mapmory.shared.parseMapmoryNotificationData
import com.mapmory.shared.data.settings.isTravelVideoCompletionNotificationEnabled
import com.mapmory.shared.data.settings.isPhotoRecordCompletionNotificationEnabled

internal const val AppUpdateNotificationChannelId = "app-updates"
internal const val PhotoRecordNotificationChannelId = "trip-record-ready"

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
    override fun onNewToken(token: String) {
        syncPushToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val completion = parseMapmoryNotificationData(message.data)
        if (completion != null) {
            showCompletionNotification(completion)
            return
        }
        if (message.data["type"] in setOf("PHOTO_RECORD_COMPLETED", "VIDEO_COMPLETED")) return
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

    private fun showCompletionNotification(target: MapmoryNotificationTarget) {
        val isRecord = target is MapmoryNotificationTarget.Record
        val targetId = when (target) {
            is MapmoryNotificationTarget.Record -> target.recordId
            is MapmoryNotificationTarget.Video -> target.jobId
        }
        val enabled = if (isRecord) isPhotoRecordCompletionNotificationEnabled(this)
            else isTravelVideoCompletionNotificationEnabled(this)
        if (!enabled || isMapmoryAppInForeground(this)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val manager = getSystemService(NotificationManager::class.java)
        val channelId = if (isRecord) PhotoRecordNotificationChannelId else TravelVideoNotificationChannelId
        manager.createNotificationChannel(
            NotificationChannel(channelId, if (isRecord) "여행 기록 완료" else "여행 영상", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        ) return

        val intent = Intent(this, MainActivity::class.java).apply {
            action = if (isRecord) MapmoryNotificationIntent.ACTION_OPEN_RECORD else MapmoryNotificationIntent.ACTION_OPEN_VIDEO
            putExtra(if (isRecord) MapmoryNotificationIntent.EXTRA_RECORD_ID else MapmoryNotificationIntent.EXTRA_VIDEO_JOB_ID, targetId)
            data = Uri.parse("mapmory://notification/${if (isRecord) "record" else "video"}/${targetId}")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            targetId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val readyNotification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_pin)
            .setContentTitle(if (isRecord) "사진 업로드 완료" else "영상이 완성됐어요")
            .setContentText(if (isRecord) "저장된 여행 기록을 확인해 보세요." else "여행 영상을 확인해 보세요.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify("${if (isRecord) "photo-record" else "share-video"}-${targetId}", targetId.hashCode(), readyNotification)
    }
}

private const val TravelVideoNotificationChannelId = "share-video-ready"
