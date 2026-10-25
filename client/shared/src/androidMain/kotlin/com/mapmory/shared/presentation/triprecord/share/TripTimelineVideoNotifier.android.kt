package com.mapmory.shared.presentation.triprecord.share

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberTripTimelineVideoNotifier(): TripTimelineVideoNotifier {
    val context = LocalContext.current.applicationContext
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    return remember(context, permissionLauncher) {
        AndroidTripTimelineVideoNotifier(context, permissionLauncher)
    }
}

private class AndroidTripTimelineVideoNotifier(
    private val context: Context,
    private val permissionLauncher: ActivityResultLauncher<String>,
) : TripTimelineVideoNotifier {
    override fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override suspend fun showReady(jobId: Long): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!canPostNotifications(manager)) return false
        ensureChannel(manager)
        clearProgress(jobId)
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("여행 영상이 준비됐어요")
            .setContentText("앱에서 미리 보고 저장하거나 공유해 보세요.")
            .setAutoCancel(true)
            .apply { launchPendingIntent()?.let { setContentIntent(it) } }
            .build()
        manager.notify("share-video-$jobId", jobId.hashCode(), notification)
        return true
    }

    override fun showProgress(jobId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!canPostNotifications(manager)) return
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("여행 영상 생성 중")
            .setContentText("앱 화면을 닫아도 생성은 계속돼요.")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { launchPendingIntent()?.let { setContentIntent(it) } }
            .build()
        manager.notify("share-video-$jobId", jobId.hashCode(), notification)
    }

    override fun clearProgress(jobId: Long) {
        context.getSystemService(NotificationManager::class.java)
            .cancel("share-video-$jobId", jobId.hashCode())
    }

    private fun ensureChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(ChannelId, "여행 영상", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun canPostNotifications(manager: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            manager.getNotificationChannel(ChannelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun launchPendingIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context,
                NotificationRequestCode,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
}

private const val ChannelId = "share-video-ready"
private const val NotificationRequestCode = 31002
