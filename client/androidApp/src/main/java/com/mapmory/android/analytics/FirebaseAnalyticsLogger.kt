package com.mapmory.android.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
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

    private val crashlytics = firebaseAnalytics?.let {
        runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
    }

    override fun logEvent(name: String, parameters: Map<String, String>) {
        collectingCrashlytics()?.let { client ->
            CrashlyticsEventContext.make(name, parameters)?.let { context ->
                client.setCustomKeys(CustomKeysAndValues.Builder().apply {
                    context.keys.forEach { (key, value) -> putString(key, value) }
                }.build())
                client.log(context.log)
            }
        }
        firebaseAnalytics?.logEvent(
            name,
            Bundle().apply {
                parameters.forEach { (key, value) ->
                    if (key in setOf("duration_ms", "elapsed_ms", "result_count", "http_status") && value.toLongOrNull() != null) {
                        putLong(key, value.toLong())
                    } else putString(key, value)
                }
                putString("event_schema_version", "2")
            },
        )
    }

    override fun setUserProperty(name: String, value: String?) {
        firebaseAnalytics?.setUserProperty(name, value)
        if (name == "is_team") {
            collectingCrashlytics()?.setCustomKey(name, CrashlyticsEventContext.teamValue(value))
        }
    }

    private fun collectingCrashlytics(): FirebaseCrashlytics? =
        crashlytics?.takeIf { it.isCrashlyticsCollectionEnabled }
}

private const val InternalPackageSuffix = ".internal"
