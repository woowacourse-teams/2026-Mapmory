package com.mapmory.android

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mapmory.shared.MapmoryApp

private val SystemBarColor = Color(0xFF07171B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(SystemBarColor.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(SystemBarColor.toArgb()),
        )
        setContent {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = SystemBarColor,
                contentWindowInsets = WindowInsets.safeDrawing,
            ) { contentPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    MapmoryApp()
                }
            }
        }
    }
}
