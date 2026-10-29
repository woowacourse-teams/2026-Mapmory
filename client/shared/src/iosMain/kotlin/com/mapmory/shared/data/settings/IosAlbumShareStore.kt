package com.mapmory.shared.data.settings

import platform.Foundation.NSUserDefaults

class IosAlbumShareStore(apiBaseUrl: String) : AlbumShareStore {
    private val key = "mapmory_album_share:${apiBaseUrl.trimEnd('/')}"
    override fun read(): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)
    override fun write(value: String) { NSUserDefaults.standardUserDefaults.setObject(value, forKey = key) }
}
