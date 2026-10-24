package com.mapmory.shared.data.settings

import android.content.Context

internal const val NotificationPreferencesFile = "mapmory_notification_preferences"
internal const val PhotoRecordCompletionKey = "photo_record_completion"
internal const val TravelVideoCompletionKey = "travel_video_completion"

fun isPhotoRecordCompletionNotificationEnabled(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(NotificationPreferencesFile, Context.MODE_PRIVATE)
        .getBoolean(PhotoRecordCompletionKey, true)

fun isTravelVideoCompletionNotificationEnabled(context: Context): Boolean =
    context.applicationContext
        .getSharedPreferences(NotificationPreferencesFile, Context.MODE_PRIVATE)
        .getBoolean(TravelVideoCompletionKey, true)
