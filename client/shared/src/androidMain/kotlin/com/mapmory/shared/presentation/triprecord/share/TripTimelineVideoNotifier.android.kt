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

    override fun showReady(jobId: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ChannelId, "여행 영상", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                NotificationRequestCode,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("여행 영상이 준비됐어요")
            .setContentText("앱에서 미리 보고 저장하거나 공유해 보세요.")
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()
        manager.notify("share-video-$jobId", jobId.hashCode(), notification)
    }
}

private const val ChannelId = "share-video-ready"
private const val NotificationRequestCode = 31002
