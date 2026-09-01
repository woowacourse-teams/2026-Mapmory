package com.mapmory.android.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.mapmory.shared.analytics.MapmoryAnalytics

class FirebaseAnalyticsLogger(context: Context) : MapmoryAnalytics {
    private val firebaseAnalytics = runCatching {
        FirebaseApp.initializeApp(context.applicationContext)?.let {
            FirebaseAnalytics.getInstance(context.applicationContext)
        }
    }.getOrNull()

    override fun logEvent(name: String, parameters: Map<String, String>) {
        firebaseAnalytics?.logEvent(
            name,
            Bundle().apply {
                parameters.forEach { (key, value) -> putString(key, value) }
            },
        )
    }
}
