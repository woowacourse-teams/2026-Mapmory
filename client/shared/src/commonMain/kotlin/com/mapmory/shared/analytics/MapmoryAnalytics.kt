package com.mapmory.shared.analytics

import androidx.compose.runtime.staticCompositionLocalOf

interface MapmoryAnalytics {
    fun logEvent(
        name: String,
        parameters: Map<String, String> = emptyMap(),
    )
}

object NoOpMapmoryAnalytics : MapmoryAnalytics {
    override fun logEvent(name: String, parameters: Map<String, String>) = Unit
}

val LocalMapmoryAnalytics = staticCompositionLocalOf<MapmoryAnalytics> {
    NoOpMapmoryAnalytics
}

object MapmoryAnalyticsEvent {
    const val SCREEN_VIEW = "screen_view"
    const val BOTTOM_NAV_CLICKED = "bottom_nav_clicked"
    const val MAP_SCOPE_CHANGED = "map_scope_changed"
    const val MAP_PROVINCE_SELECTED = "map_province_selected"
    const val MAP_LOCATION_SELECTED = "map_location_selected"
    const val MAP_DETAIL_BACK_CLICKED = "map_detail_back_clicked"
    const val RECORD_CREATE_STARTED = "record_create_started"
    const val RECORD_SAVE_STARTED = "record_save_started"
    const val RECORD_SAVE_COMPLETED = "record_save_completed"
    const val RECORD_SAVE_FAILED = "record_save_failed"
    const val PHOTO_PICKER_OPENED = "photo_picker_opened"
    const val PHOTOS_ADDED = "photos_added"
    const val PHOTO_RECOMMENDATION_STARTED = "photo_recommendation_started"
    const val PHOTO_RECOMMENDATION_CANCELLED = "photo_recommendation_cancelled"
    const val JOURNAL_RECORD_OPENED = "journal_record_opened"
    const val JOURNAL_RETRY_CLICKED = "journal_retry_clicked"
    const val JOURNAL_FILTER_SELECTED = "journal_filter_selected"
    const val JOURNAL_PAGE_CHANGED = "journal_page_changed"
    const val SETTINGS_OPENED = "settings_opened"
    const val THEME_CHANGED = "theme_changed"
    const val PRIVACY_POLICY_OPENED = "privacy_policy_opened"
}
