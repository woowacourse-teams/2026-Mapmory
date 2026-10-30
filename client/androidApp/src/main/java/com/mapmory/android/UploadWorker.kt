package com.mapmory.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.mapmory.shared.app.*
import com.mapmory.shared.data.settings.isPhotoRecordCompletionNotificationEnabled
import kotlinx.coroutines.flow.first

/** The process-wide container is shared by the worker and UI to avoid duplicate upload queues. */
internal class AndroidBackgroundSaveExecution(private val context: Context, namespace: String) : BackgroundSaveExecution {
    override val store = AndroidBackgroundSaveStore(context, namespace)
    override fun attach(saver: BackgroundTripRecordSaver) {
        if (saver.saves.value.any { it.status == BackgroundSaveStatus.QUEUED }) onQueued()
    }
    override fun onQueued() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "trip-record-uploads", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
    override suspend fun <T> run(block: suspend () -> T): T = block()
    override fun onProgress(save: BackgroundTripRecordSave) {
        if (isPhotoRecordCompletionNotificationEnabled(context) && canNotify()) {
            manager().notify(UploadNotificationId, notification(context, save.title, "${save.progressPercent}% 업로드 중", save.progressPercent))
        }
    }
    override fun onCompleted(save: BackgroundTripRecordSave) {
        if (isPhotoRecordCompletionNotificationEnabled(context) && canNotify()) manager().notify(
            save.id.hashCode(),
            notification(context, "사진 업로드 완료", "${save.locationName} 기록이 저장됐어요", null),
        )
    }
    private fun manager() = context.getSystemService(NotificationManager::class.java)
    private fun canNotify() = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class UploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        setForeground(ForegroundInfo(
            UploadNotificationId,
            notification(applicationContext, "사진 업로드", "기록을 업로드하고 있어요", 0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        ))
        val saver = UploadContainer.get(applicationContext).backgroundTripRecordSaver
        val terminal = saver.saves.first { saves -> saves.all { it.status == BackgroundSaveStatus.FAILED } }
        return if (terminal.isEmpty()) Result.success() else Result.failure()
    }
}

private const val UploadNotificationId = 31001
private const val UploadChannel = "trip-record-uploads"
private fun notification(context: Context, title: String, text: String, progress: Int?): Notification {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(UploadChannel, "사진 업로드", NotificationManager.IMPORTANCE_LOW),
    )
    val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    return NotificationCompat.Builder(context, UploadChannel)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(title).setContentText(text).setContentIntent(intent)
        .setOnlyAlertOnce(true).setOngoing(progress != null).setAutoCancel(progress == null)
        .apply { if (progress != null) setProgress(100, progress, progress == 0) }
        .build()
}
