package com.mapmory.android

import android.os.Bundle
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.lifecycleScope
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.MapmoryNavigation
import com.mapmory.shared.MapmoryNotificationIntent
import com.mapmory.android.analytics.FirebaseAnalyticsLogger
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.data.settings.isPhotoRecordCompletionNotificationEnabled
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private var processHasStarted = false
        private const val STATE_NOTIFICATION_ACTION = "notification_action"
        private const val STATE_NOTIFICATION_TARGET_ID = "notification_target_id"
    }

    private val appViewModel: MapmoryAppViewModel by viewModels()
    private val navigation: MapmoryNavigation get() = appViewModel.navigation
    private val analyticsLogger by lazy { FirebaseAnalyticsLogger(applicationContext) }
    private val developerToolsInfo by lazy { loadDeveloperToolsInfo() }
    private var pendingNotificationAction: String? = null
    private var pendingNotificationTargetId: Long? = null

    override fun onResume() {
        super.onResume()
        appViewModel.forceUpdate.refresh()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent?.dataString?.let { appViewModel.invitations.receive(it) }
        lifecycleScope.launch { restoreInstallInvitation(applicationContext, appViewModel.invitations) }
        pendingNotificationAction = savedInstanceState?.getString(STATE_NOTIFICATION_ACTION)
        pendingNotificationTargetId = savedInstanceState?.getLong(STATE_NOTIFICATION_TARGET_ID)?.takeIf { it > 0L }
        val restoredAction = pendingNotificationAction
        val restoredTargetId = pendingNotificationTargetId
        if (restoredAction != null && restoredTargetId != null) {
            // ViewModel state survives rotation; saved state restores the request after process death.
            if (!navigation.hasPendingNotification()) {
                val extra = if (restoredAction == MapmoryNotificationIntent.ACTION_OPEN_RECORD) {
                    MapmoryNotificationIntent.EXTRA_RECORD_ID
                } else {
                    MapmoryNotificationIntent.EXTRA_VIDEO_JOB_ID
                }
                handleNotificationIntent(
                    Intent(this, MainActivity::class.java)
                        .setAction(restoredAction)
                        .putExtra(extra, restoredTargetId),
                    preserveBackStack = false,
                )
            }
            setIntent(Intent(this, MainActivity::class.java))
        } else if (handleNotificationIntent(intent, preserveBackStack = processHasStarted && savedInstanceState != null)) {
            setIntent(Intent(this, MainActivity::class.java))
        }
        processHasStarted = true
        lifecycleScope.launch {
            navigation.notificationPending.collect { isPending ->
                if (!isPending) {
                    pendingNotificationAction = null
                    pendingNotificationTargetId = null
                }
            }
        }
        val notificationPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {}
        lifecycleScope.launch {
            appViewModel.container.backgroundTripRecordSaver.saves.collect { saves ->
                val preferences = getSharedPreferences("upload-notifications", MODE_PRIVATE)
                if (isPhotoRecordCompletionNotificationEnabled(applicationContext) &&
                    Build.VERSION.SDK_INT >= 33 && saves.isNotEmpty() &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    !preferences.getBoolean("requested", false)
                ) {
                    preferences.edit().putBoolean("requested", true).apply()
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        setContent {
            var lastBackPressedAt by remember { mutableLongStateOf(0L) }
            var isDarkTheme by rememberSaveable { mutableStateOf(false) }
            val systemBarColor = colorResource(
                if (isDarkTheme) R.color.mapmory_system_bar_dark else R.color.mapmory_system_bar_light,
            )

            SideEffect {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(systemBarColor.toArgb())
                } else {
                    SystemBarStyle.light(systemBarColor.toArgb(), systemBarColor.toArgb())
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle,
                )
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = systemBarColor,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.mapmory.shared.presentation.auth.LocalSocialLoginLauncher provides
                            androidx.compose.runtime.remember { AndroidSocialLoginLauncher(this@MainActivity) },
                    ) {
                        MapmoryApp(
                            container = appViewModel.container,
                            navigation = navigation,
                            analytics = analyticsLogger,
                            contentWindowInsets = WindowInsets.safeDrawing,
                            onThemeChanged = { isDarkTheme = it },
                            developerToolsInfo = developerToolsInfo,
                            updateState = appViewModel.forceUpdate.state,
                            onRetryUpdateCheck = appViewModel.forceUpdate::refresh,
                        )
                    }

                    // Dispatch to the active screen before NavHost can pop the entire destination.
                    BackHandler {
                        if (navigation.popBackStack()) {
                            lastBackPressedAt = 0L
                        } else {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastBackPressedAt < ExitBackPressIntervalMs) {
                                finish()
                            } else {
                                lastBackPressedAt = now
                                Toast.makeText(
                                    this@MainActivity,
                                    "한 번 더 누르면 앱을 종료합니다.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { appViewModel.invitations.receive(it) }
        setIntent(intent)
        if (handleNotificationIntent(intent, preserveBackStack = true)) {
            setIntent(Intent(this, MainActivity::class.java))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (navigation.hasPendingNotification()) {
            pendingNotificationAction?.let { outState.putString(STATE_NOTIFICATION_ACTION, it) }
            pendingNotificationTargetId?.let { outState.putLong(STATE_NOTIFICATION_TARGET_ID, it) }
        }
    }

    private fun handleNotificationIntent(intent: Intent?, preserveBackStack: Boolean): Boolean {
        intent ?: return false
        val action = intent.action
        val targetId = when (action) {
            MapmoryNotificationIntent.ACTION_OPEN_RECORD -> intent
                .getLongExtra(MapmoryNotificationIntent.EXTRA_RECORD_ID, 0L)
                .takeIf { it > 0L }
            MapmoryNotificationIntent.ACTION_OPEN_VIDEO -> intent
                .getLongExtra(MapmoryNotificationIntent.EXTRA_VIDEO_JOB_ID, 0L)
                .takeIf { it > 0L }
            null, Intent.ACTION_MAIN -> intent.extras?.let { extras ->
                sequenceOf(MapmoryNotificationIntent.EXTRA_VIDEO_JOB_ID, "jobId", "job_id")
                    .mapNotNull { key -> extras.get(key)?.toString()?.toLongOrNull() }
                    .firstOrNull { it > 0L }
            }
            else -> null
        } ?: return false

        val isRecord = action == MapmoryNotificationIntent.ACTION_OPEN_RECORD
        pendingNotificationAction = if (isRecord) {
            MapmoryNotificationIntent.ACTION_OPEN_RECORD
        } else {
            MapmoryNotificationIntent.ACTION_OPEN_VIDEO
        }
        pendingNotificationTargetId = targetId
        if (isRecord) {
            navigation.openRecordNotification(targetId, preserveBackStack)
        } else {
            navigation.openVideoNotification(targetId, preserveBackStack)
        }
        return true
    }
}

@Suppress("DEPRECATION")
private fun MainActivity.loadDeveloperToolsInfo(): MapmoryDeveloperToolsInfo? {
    val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!isDebuggable) return null

    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode.toLong()
    }
    return MapmoryDeveloperToolsInfo(
        versionName = packageInfo.versionName.orEmpty(),
        versionCode = versionCode,
        applicationId = packageName,
        buildType = "debug",
        apiBaseUrl = getString(R.string.mapmory_api_base_url),
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )
}

private const val ExitBackPressIntervalMs = 2_000L
