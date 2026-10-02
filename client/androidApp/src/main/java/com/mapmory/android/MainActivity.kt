package com.mapmory.android

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.MapmoryNavigation
import com.mapmory.android.analytics.FirebaseAnalyticsLogger

class MainActivity : ComponentActivity() {
    private val appViewModel: MapmoryAppViewModel by viewModels()
    private val analyticsLogger by lazy { FirebaseAnalyticsLogger(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

private const val ExitBackPressIntervalMs = 2_000L
