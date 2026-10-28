package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

internal const val PhotoRecordCompletionKey = "mapmory_photo_record_completion_notification"
internal const val TravelVideoCompletionKey = "mapmory_travel_video_completion_notification"

fun isPhotoRecordCompletionNotificationEnabled(): Boolean = isEnabled(PhotoRecordCompletionKey)

fun isTravelVideoCompletionNotificationEnabled(): Boolean = isEnabled(TravelVideoCompletionKey)

private fun isEnabled(key: String): Boolean {
    val preferences = NSUserDefaults.standardUserDefaults
    return preferences.objectForKey(key) == null || preferences.boolForKey(key)
}
