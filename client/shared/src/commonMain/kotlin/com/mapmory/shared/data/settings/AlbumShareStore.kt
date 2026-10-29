package com.mapmory.shared.data.settings

/** Stores only operation IDs and stages, never record contents or photo URLs. */
interface AlbumShareStore {
    fun read(): String?
    fun write(value: String)
}

class MemoryAlbumShareStore : AlbumShareStore {
    private var value: String? = null
    override fun read() = value
    override fun write(value: String) { this.value = value }
}
