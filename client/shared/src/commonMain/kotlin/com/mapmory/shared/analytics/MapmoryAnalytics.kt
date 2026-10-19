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
    const val RECORD_BACKGROUND_SAVE_FAILED = "record_background_save_failed"
    const val RECORD_SAVE_RETRY = "record_save_retry"
    const val RECORD_SAVE_DISCARDED = "record_save_discarded"
    const val RECORD_INTRODUCTION_VIEWED = "record_introduction_viewed"
    const val RECORD_INTRODUCTION_COMPLETED = "record_introduction_completed"
    const val MAP_RETRY_CLICKED = "map_retry_clicked"
    const val OPERATION_COMPLETED = "app_operation_completed"
    const val APP_ERROR = "app_error"
    const val RECORD_VIEWED = "record_viewed"
    const val RECORD_EDIT_STARTED = "record_edit_started"
    const val RECORD_DELETE_STARTED = "record_delete_started"
    const val RECORD_DELETED = "record_deleted"
    const val RECORD_PHOTO_OPENED = "record_photo_opened"
    const val PHOTO_RECOMMENDATION_PAGE_LOADED = "photo_recommendation_page_loaded"
    const val PHOTO_PERMISSION_REQUIRED = "photo_permission_required"
    const val FEEDBACK_OPENED = "feedback_opened"
    const val FEEDBACK_SUBMITTED = "feedback_submitted"
    const val TAG_CREATED = "tag_created"
    const val AUTH_SESSION_REFRESH_FAILED = "auth_session_refresh_failed"
    const val SCREEN_VIEW = "app_screen_view"
    const val BOTTOM_NAV_CLICKED = "bottom_nav_clicked"
    const val MAP_SCOPE_CHANGED = "map_scope_changed"
    const val MAP_PROVINCE_SELECTED = "map_province_selected"
    const val MAP_LOCATION_SELECTED = "map_location_selected"
    const val RECORD_LOCATION_SELECTED = "record_location_selected"
    const val MAP_DETAIL_BACK_CLICKED = "map_detail_back_clicked"
    const val RECORD_CREATE_STARTED = "record_create_started"
    const val RECORD_FLOW_STEP_VIEWED = "record_flow_step_viewed"
    const val RECORD_EDITOR_FIELD_INTERACTED = "record_editor_field_interacted"
    const val RECORD_EDITOR_EXITED = "record_editor_exited"
    const val RECORD_SAVE_STARTED = "record_save_started"
    const val RECORD_SAVE_QUEUED = "record_save_queued"
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
    const val STATISTICS_RETRY_CLICKED = "statistics_retry_clicked"
    const val SETTINGS_OPENED = "settings_opened"
    const val THEME_CHANGED = "theme_changed"
    const val PRIVACY_POLICY_OPENED = "privacy_policy_opened"
}
