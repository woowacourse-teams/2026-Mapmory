package com.mapmory.android

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmory.shared.app.BackgroundSaveStatus
import com.mapmory.shared.app.BackgroundTripRecordSave
import com.mapmory.shared.app.AndroidBackgroundSaveStore
import com.mapmory.shared.app.PendingTripRecordSave
import com.mapmory.shared.domain.model.TripRecordDraft
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises the real notification bridge without uploading test records to a user's account. */
class UploadNotificationLifecycleTest {
    @Test
    fun pendingUploadAndUncertainServerSaveSurviveStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "upload-lifecycle-test-${System.nanoTime()}"
        val store = AndroidBackgroundSaveStore(context, namespace)
        val draft = TripRecordDraft(locationId = 1, startDate = "2026-11-05", mediaObjectKeys = emptyList(), title = "복구 확인")
        val pending = listOf(
            PendingTripRecordSave(1, "서울", draft),
            PendingTripRecordSave(2, "서울", draft, serverSaveStarted = true),
        )
        try {
            store.write(pending)
            assertEquals(pending, AndroidBackgroundSaveStore(context, namespace).read())
        } finally {
            store.write(emptyList())
        }
    }

    @Test
    fun visibleActivitySuppressesCompletionAndStoppedActivityPostsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val preferences = context.getSharedPreferences("mapmory_notification_preferences", Context.MODE_PRIVATE)
        val previous = preferences.all["photo_record_completion"] as? Boolean
        val id = 987654321L
        val save = BackgroundTripRecordSave(id, "알림 검증", "제주", "2026-11-05", 2, BackgroundSaveStatus.SAVING)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        }
        preferences.edit().putBoolean("photo_record_completion", true).commit()
        try {
            val execution = AndroidBackgroundSaveExecution(context, "notification-lifecycle-test")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                waitForImportance(foreground = true)
                execution.onCompleted(save, id)
                assertEquals(0, manager.activeNotifications.count { it.id == id.hashCode() })

                // In-app navigation does not stop the host Activity.
                scenario.onActivity { it.title = "다른 화면" }
                execution.onCompleted(save, id)
                assertEquals(0, manager.activeNotifications.count { it.id == id.hashCode() })

                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
                waitForImportance(foreground = false)
                execution.onCompleted(save, id)
                waitForNotification(manager, id.hashCode())
                manager.cancel(id.hashCode())

                scenario.close()
                execution.onCompleted(save, id)
                waitForNotification(manager, id.hashCode())
                manager.cancel(id.hashCode())

                preferences.edit().putBoolean("photo_record_completion", false).commit()
                execution.onCompleted(save, id)
                assertEquals(0, manager.activeNotifications.count { it.id == id.hashCode() })
            }
        } finally {
            manager.cancel(id.hashCode())
            preferences.edit().apply {
                if (previous == null) remove("photo_record_completion") else putBoolean("photo_record_completion", previous)
            }.commit()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun waitForImportance(foreground: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        do {
            val process = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(process)
            if ((process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) == foreground) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Process visibility did not become foreground=$foreground")
    }

    private fun waitForNotification(manager: NotificationManager, id: Int) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (manager.activeNotifications.none { it.id == id } && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        assertEquals(1, manager.activeNotifications.count { it.id == id })
    }
}
