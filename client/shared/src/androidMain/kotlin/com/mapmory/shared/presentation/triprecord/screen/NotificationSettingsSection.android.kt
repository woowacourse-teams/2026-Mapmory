package com.mapmory.shared.presentation.triprecord.screen

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mapmory.shared.data.settings.isPhotoRecordCompletionNotificationEnabled
import com.mapmory.shared.data.settings.isTravelVideoCompletionNotificationEnabled
import com.mapmory.shared.data.settings.NotificationPreferencesFile
import com.mapmory.shared.data.settings.PhotoRecordCompletionKey
import com.mapmory.shared.data.settings.TravelVideoCompletionKey

@Composable
internal actual fun NotificationSettingsSection() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(NotificationPreferencesFile, Context.MODE_PRIVATE)
    }
    var recordSaveEnabled by remember(preferences) {
        mutableStateOf(isPhotoRecordCompletionNotificationEnabled(context))
    }
    var travelVideoEnabled by remember(preferences) {
        mutableStateOf(isTravelVideoCompletionNotificationEnabled(context))
    }

    NotificationSettingsCard(
        recordSaveEnabled = recordSaveEnabled,
        travelVideoEnabled = travelVideoEnabled,
        onRecordSaveChanged = { enabled ->
            preferences.edit().putBoolean(PhotoRecordCompletionKey, enabled).apply()
            recordSaveEnabled = enabled
        },
        onTravelVideoChanged = { enabled ->
            preferences.edit().putBoolean(TravelVideoCompletionKey, enabled).apply()
            travelVideoEnabled = enabled
        },
        onOpenDeviceSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
    )
}
