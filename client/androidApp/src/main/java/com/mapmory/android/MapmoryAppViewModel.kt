package com.mapmory.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mapmory.android.analytics.FirebaseAnalyticsLogger
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.app.createGuestRemoteAppContainer
import com.mapmory.shared.data.auth.AndroidAuthTokenStore
import com.mapmory.shared.data.media.AndroidLocalPhotoDataSource
import com.mapmory.shared.data.media.AndroidPhotoPreviewCache
import com.mapmory.shared.data.repository.AndroidTripRecordListCache
import com.mapmory.shared.data.repository.AndroidMapSummaryCache
import com.mapmory.shared.data.repository.AndroidTripStatisticsCache
import com.mapmory.shared.data.settings.AndroidOnboardingPreference
import com.mapmory.shared.data.settings.AndroidShareVideoJobStore
import com.mapmory.shared.data.settings.AndroidTeamDevicePreference
import com.mapmory.shared.data.settings.AndroidThemePreference
import com.mapmory.shared.data.remote.MapmoryApiException

class MapmoryAppViewModel(application: Application) : AndroidViewModel(application) {
    val forceUpdate = com.mapmory.android.update.AndroidForceUpdateController(application)
    override fun onCleared() { forceUpdate.close() }

    val container: AppContainer = UploadContainer.get(application)
}

internal object UploadContainer {
    @Volatile private var instance: AppContainer? = null

    @Synchronized
    fun get(context: android.content.Context): AppContainer {
        instance?.let { return it }
        val application = context.applicationContext
        val isDebuggable =
            (application.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val configuredApiBaseUrl = application.getString(R.string.mapmory_api_base_url)
        val execution = AndroidBackgroundSaveExecution(application, configuredApiBaseUrl)
        return createGuestRemoteAppContainer(
            apiBaseUrl = configuredApiBaseUrl,
            tokenStore = AndroidAuthTokenStore(application, configuredApiBaseUrl),
            photoPreviewCache = AndroidPhotoPreviewCache(application),
            localPhotoDataSource = AndroidLocalPhotoDataSource(application),
            mapSummaryCache = AndroidMapSummaryCache(application),
            tripStatisticsCache = AndroidTripStatisticsCache(application),
            tripRecordListCache = AndroidTripRecordListCache(application, configuredApiBaseUrl),
            themePreference = AndroidThemePreference(application),
            onboardingPreference = AndroidOnboardingPreference(application),
            teamDevicePreference = AndroidTeamDevicePreference(application),
            shareVideoJobStore = AndroidShareVideoJobStore(application, configuredApiBaseUrl),
            albumShareStore = com.mapmory.shared.data.settings.AndroidAlbumShareStore(application, configuredApiBaseUrl),
            backgroundSaveExecution = execution,
            onAuthRefreshFailed = { stage, error ->
                if (isDebuggable) {
                    val reason = (error as? MapmoryApiException)
                        ?.let { "HTTP ${it.statusCode} ${it.code}" }
                        ?: (error::class.simpleName ?: "unknown")
                    android.util.Log.d("MapmoryAuth", "refresh failed stage=$stage: $reason")
                }
            },
            analytics = FirebaseAnalyticsLogger(application),
        ).also { instance = it }
    }
}
