package com.mapmory.shared.presentation.triprecord.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.domain.model.ServiceFeedbackCategory
import com.mapmory.shared.domain.repository.ServiceFeedbackRepository
import com.mapmory.shared.presentation.feedback.currentServiceFeedbackPlatform
import com.mapmory.shared.presentation.triprecord.screen.TripSettingsScreen

@Composable
internal fun TripSettingsRoute(
    onOpenMap: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    developerToolsInfo: MapmoryDeveloperToolsInfo?,
    onOpenDeveloperTools: () -> Unit,
    serviceFeedbackRepository: ServiceFeedbackRepository?,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalMapmoryAnalytics.current
    LaunchedEffect(Unit) {
        analytics.logEvent(MapmoryAnalyticsEvent.SETTINGS_OPENED)
        analytics.logEvent(
            MapmoryAnalyticsEvent.SCREEN_VIEW,
            mapOf("screen_name" to "settings"),
        )
    }

    TripSettingsScreen(
        modifier = modifier,
        onMapClick = onOpenMap,
        onRecordClick = onOpenRecords,
        onProfileClick = onOpenProfile,
        onSettingsClick = onOpenSettings,
        developerToolsInfo = developerToolsInfo,
        onOpenDeveloperTools = onOpenDeveloperTools,
        onSubmitFeedback = { category: ServiceFeedbackCategory, content: String ->
            serviceFeedbackRepository?.submit(category, content, currentServiceFeedbackPlatform())
                ?: Result.failure(IllegalStateException("의견 보내기를 사용할 수 없습니다."))
        },
    )
}
