package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mapmory.shared.data.settings.PhotoRecordCompletionKey
import com.mapmory.shared.data.settings.TravelVideoCompletionKey
import com.mapmory.shared.data.settings.isPhotoRecordCompletionNotificationEnabled
import com.mapmory.shared.data.settings.isTravelVideoCompletionNotificationEnabled
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
internal actual fun NotificationSettingsSection() {
    val preferences = remember { NSUserDefaults.standardUserDefaults }
    var recordSaveEnabled by remember {
        mutableStateOf(isPhotoRecordCompletionNotificationEnabled())
    }
    var travelVideoEnabled by remember {
        mutableStateOf(isTravelVideoCompletionNotificationEnabled())
    }

    NotificationSettingsCard(
        recordSaveEnabled = recordSaveEnabled,
        travelVideoEnabled = travelVideoEnabled,
        onRecordSaveChanged = { enabled ->
            preferences.setBool(enabled, forKey = PhotoRecordCompletionKey)
            recordSaveEnabled = enabled
        },
        onTravelVideoChanged = { enabled ->
            preferences.setBool(enabled, forKey = TravelVideoCompletionKey)
            travelVideoEnabled = enabled
        },
        onOpenDeviceSettings = {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
            }
        },
    )
}
