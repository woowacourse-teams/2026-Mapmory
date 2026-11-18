package com.mapmory.android

import android.content.Context
import androidx.compose.runtime.Composable
import com.mapmory.shared.app.AppContainer
import io.ktor.client.HttpClient

internal object AuthReproduction {
    fun attach(context: Context, client: HttpClient) = Unit
    @Composable fun Overlay(container: AppContainer) = Unit
}
