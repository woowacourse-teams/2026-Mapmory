package com.mapmory.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mapmory.shared.data.auth.AndroidAuthTokenStore
import com.mapmory.shared.data.media.AndroidPhotoPreviewCache
import com.mapmory.shared.data.repository.AndroidTripStatisticsCache
import com.mapmory.shared.data.repository.AndroidMapSummaryCache
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.app.MAPMORY_API_BASE_URL
import com.mapmory.shared.app.createGuestRemoteAppContainer

class MapmoryAppViewModel(application: Application) : AndroidViewModel(application) {
    private val configuredApiBaseUrl = application.getString(R.string.mapmory_api_base_url)
        .takeIf(String::isNotBlank)
        ?: MAPMORY_API_BASE_URL

    val container: AppContainer = createGuestRemoteAppContainer(
        apiBaseUrl = configuredApiBaseUrl,
        tokenStore = AndroidAuthTokenStore(application),
        photoPreviewCache = AndroidPhotoPreviewCache(application),
        mapSummaryCache = AndroidMapSummaryCache(application),
        tripStatisticsCache = AndroidTripStatisticsCache(application),
    )

    override fun onCleared() {
        container.close()
    }
}
