package com.mapmory.shared.developer

data class MapmoryDeveloperToolsInfo(
    val versionName: String,
    val versionCode: Long,
    val applicationId: String,
    val buildType: String,
    val apiBaseUrl: String,
    val deviceName: String,
    val androidVersion: String,
)
