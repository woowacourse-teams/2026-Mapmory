package com.mapmory.shared.data.settings

import android.content.Context

class AndroidTeamDevicePreference(context: Context) : TeamDevicePreference {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    override fun loadIsTeamDevice(): Boolean = preferences.getBoolean(IsTeamDeviceKey, false)

    override fun saveIsTeamDevice(isTeamDevice: Boolean) {
        preferences.edit().putBoolean(IsTeamDeviceKey, isTeamDevice).apply()
    }
}

private const val PreferencesName = "mapmory_settings"
private const val IsTeamDeviceKey = "is_team_device"
