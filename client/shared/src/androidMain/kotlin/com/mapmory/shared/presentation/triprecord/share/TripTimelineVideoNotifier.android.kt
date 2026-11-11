package com.mapmory.shared.presentation.triprecord.share

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.mapmory.shared.data.settings.isTravelVideoCompletionNotificationEnabled
import com.mapmory.shared.MapmoryNotificationIntent
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface AndroidNotificationRegistrationHost {
    suspend fun refreshNotificationRegistration()
}

@Composable
internal actual fun rememberTripTimelineVideoNotifier(): TripTimelineVideoNotifier {
    val hostContext = LocalContext.current
    val context = hostContext.applicationContext
    val registrationHost = generateSequence(hostContext) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<AndroidNotificationRegistrationHost>().firstOrNull()
    var permissionResult by remember { mutableStateOf<CancellableContinuation<Unit>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionResult?.let { if (it.isActive) it.resume(Unit) }
        permissionResult = null
    }
    return remember(context, permissionLauncher, registrationHost) {
        AndroidTripTimelineVideoNotifier(context, permissionLauncher, registrationHost) { permissionResult = it }
    }
}

private class AndroidTripTimelineVideoNotifier(
    private val context: Context,
    private val permissionLauncher: ActivityResultLauncher<String>,
    private val registrationHost: AndroidNotificationRegistrationHost?,
    private val setPermissionResult: (CancellableContinuation<Unit>?) -> Unit,
) : TripTimelineVideoNotifier {
    override suspend fun requestPermissionIfNeeded() {
        if (isTravelVideoCompletionNotificationEnabled(context) && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            suspendCancellableCoroutine<Unit> { continuation ->
                setPermissionResult(continuation)
                continuation.invokeOnCancellation { setPermissionResult(null) }
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        registrationHost?.refreshNotificationRegistration()
    }

    override suspend fun showReady(jobId: Long): Boolean {
        if (!isTravelVideoCompletionNotificationEnabled(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!canPostNotifications(manager)) return false
        ensureChannel(manager)
        clearProgress(jobId)
        val notification = NotificationCompat.Builder(context, TripTimelineVideoNotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("여행 영상이 준비됐어요")
            .setContentText("앱에서 미리 보고 저장하거나 공유해 보세요.")
            .setAutoCancel(true)
            .apply { readyPendingIntent(jobId)?.let { setContentIntent(it) } }
            .build()
        manager.notify("share-video-$jobId", jobId.hashCode(), notification)
        return true
    }

    override fun showProgress(jobId: Long) {
        if (!isTravelVideoCompletionNotificationEnabled(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!canPostNotifications(manager)) return
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, TripTimelineVideoNotificationChannelId)
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
            NotificationChannel(TripTimelineVideoNotificationChannelId, "여행 영상", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun canPostNotifications(manager: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            manager.getNotificationChannel(TripTimelineVideoNotificationChannelId)?.importance != NotificationManager.IMPORTANCE_NONE
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

    private fun readyPendingIntent(jobId: Long): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.action = MapmoryNotificationIntent.ACTION_OPEN_VIDEO
            intent.putExtra(MapmoryNotificationIntent.EXTRA_VIDEO_JOB_ID, jobId)
            intent.data = Uri.parse("mapmory://notification/video/$jobId")
            PendingIntent.getActivity(
                context,
                jobId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
}

internal const val TripTimelineVideoNotificationChannelId = "share-video-ready"
private const val NotificationRequestCode = 31002
