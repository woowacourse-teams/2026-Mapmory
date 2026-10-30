package com.mapmory.android

import android.os.Bundle
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
import com.mapmory.android.analytics.FirebaseAnalyticsLogger
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.data.settings.isPhotoRecordCompletionNotificationEnabled
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appViewModel: MapmoryAppViewModel by viewModels()
    private val analyticsLogger by lazy { FirebaseAnalyticsLogger(applicationContext) }
    private val developerToolsInfo by lazy { loadDeveloperToolsInfo() }

    override fun onResume() {
        super.onResume()
        appViewModel.forceUpdate.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            val navigation = remember { MapmoryNavigation() }
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
