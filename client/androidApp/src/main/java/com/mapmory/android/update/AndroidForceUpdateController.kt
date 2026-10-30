package com.mapmory.android.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.mapmory.shared.update.ForceUpdateState

class AndroidForceUpdateController(context: Context) {
    private val isDevelopment = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 ||
        context.packageName.endsWith(".internal")
    private val isProductionRelease = context.packageName == "com.mapmory.android" && !isDevelopment
    private val minimumVersionKey = if (isDevelopment) "android_dev_min_supported_version" else "android_min_supported_version"
    private val recommendedVersionKey = if (isDevelopment) "android_dev_recommended_supported_version" else "android_recommended_supported_version"
    private val preferences = context.getSharedPreferences("force_update_policy", Context.MODE_PRIVATE)
    val state = ForceUpdateState(
        currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
        storeUrl = "https://play.google.com/store/apps/details?id=com.mapmory.android",
    )
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var fetching = false
    private var didCheckForStoreUpdate = false
    private val remoteConfig = if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseRemoteConfig.getInstance()
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(context.applicationContext) }

    init {
        state.applyMinimumVersion(preferences.getString(minimumVersionKey, "").orEmpty())
        state.applyRecommendedVersion(preferences.getString(recommendedVersionKey, "").orEmpty())
        remoteConfig?.let {
            FirebaseMessaging.getInstance().subscribeToTopic(if (isDevelopment) "mapmory-app-update-dev" else "mapmory-app-update")
            FirebaseMessaging.getInstance().unsubscribeFromTopic(if (isDevelopment) "mapmory-app-update" else "mapmory-app-update-dev")
            ensureAppUpdateNotificationChannel(context)
        }
    }

    fun refresh() {
        if (isProductionRelease && !didCheckForStoreUpdate) {
            didCheckForStoreUpdate = true
            appUpdateManager.appUpdateInfo.addOnSuccessListener { updateInfo ->
                state.reportStoreUpdateAvailability(
                    updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
                )
            }
        }
        if (fetching) return
        val remote = remoteConfig ?: return state.finishCheck()
        fetching = true
        val request = ++generation
        val timeout = Runnable {
            if (request == generation) {
                generation++
                fetching = false
                state.finishCheck()
            }
        }
        handler.postDelayed(timeout, 12_000L)
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (isDevelopment) 0L else 3_600L)
            .setFetchTimeoutInSeconds(10L)
            .build()
        remote.setConfigSettingsAsync(settings)
            .continueWithTask { remote.ensureInitialized() }
            .addOnCompleteListener {
                if (request != generation) return@addOnCompleteListener
                accept(remote)
                remote.fetchAndActivate().addOnCompleteListener { result ->
                    if (request != generation) return@addOnCompleteListener
                    if (result.isSuccessful) accept(remote)
                    handler.removeCallbacks(timeout)
                    fetching = false
                    state.finishCheck()
                }
            }
    }

    private fun accept(remote: FirebaseRemoteConfig) {
        val minimumVersion = remote.getString(minimumVersionKey)
        if (state.applyMinimumVersion(minimumVersion)) {
            preferences.edit().putString(minimumVersionKey, minimumVersion.trim()).apply()
        }
        val recommendedVersion = remote.getString(recommendedVersionKey)
        if (state.applyRecommendedVersion(recommendedVersion)) {
            preferences.edit().putString(recommendedVersionKey, recommendedVersion.trim()).apply()
        }
    }

    fun close() {
        generation++
        handler.removeCallbacksAndMessages(null)
    }
}
