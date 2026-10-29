package com.mapmory.shared.data.settings

import android.content.Context

class AndroidAlbumShareStore(context: Context, apiBaseUrl: String) : AlbumShareStore {
    private val preferences = context.applicationContext.getSharedPreferences("mapmory_album_share", Context.MODE_PRIVATE)
    private val key = apiBaseUrl.trimEnd('/')
    override fun read(): String? = preferences.getString(key, null)
    override fun write(value: String) { check(preferences.edit().putString(key, value).commit()) }
}
