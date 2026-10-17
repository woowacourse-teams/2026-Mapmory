package com.mapmory.shared.data.settings

interface TeamDevicePreference {
    fun loadIsTeamDevice(): Boolean

    fun saveIsTeamDevice(isTeamDevice: Boolean)
}

class MemoryTeamDevicePreference(
    initialIsTeamDevice: Boolean = false,
) : TeamDevicePreference {
    private var isTeamDevice = initialIsTeamDevice

    override fun loadIsTeamDevice(): Boolean = isTeamDevice

    override fun saveIsTeamDevice(isTeamDevice: Boolean) {
        this.isTeamDevice = isTeamDevice
    }
}
