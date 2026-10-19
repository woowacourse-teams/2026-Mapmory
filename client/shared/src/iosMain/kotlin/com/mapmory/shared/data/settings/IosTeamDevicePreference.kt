package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

class IosTeamDevicePreference : TeamDevicePreference {
    private val preferences = NSUserDefaults.standardUserDefaults

    override fun loadIsTeamDevice(): Boolean = preferences.boolForKey(IsTeamDeviceKey)

    override fun saveIsTeamDevice(isTeamDevice: Boolean) {
        preferences.setBool(isTeamDevice, forKey = IsTeamDeviceKey)
    }
}

private const val IsTeamDeviceKey = "mapmory_is_team_device"
