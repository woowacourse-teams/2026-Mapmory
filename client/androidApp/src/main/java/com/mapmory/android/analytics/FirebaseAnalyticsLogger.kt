package com.mapmory.android.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.mapmory.shared.analytics.MapmoryAnalytics

class FirebaseAnalyticsLogger(context: Context) : MapmoryAnalytics {
    private val firebaseAnalytics = context.applicationContext
        .takeUnless { it.packageName.endsWith(InternalPackageSuffix) }
        ?.let { applicationContext ->
            runCatching {
                FirebaseApp.initializeApp(applicationContext)?.let {
                    FirebaseAnalytics.getInstance(applicationContext)
                }
            }.getOrNull()
        }

    override fun logEvent(name: String, parameters: Map<String, String>) {
        firebaseAnalytics?.logEvent(
            name,
            Bundle().apply {
                parameters.forEach { (key, value) -> putString(key, value) }
            },
        )
    }
}

private const val InternalPackageSuffix = ".internal"
