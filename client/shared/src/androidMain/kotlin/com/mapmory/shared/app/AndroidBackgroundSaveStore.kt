package com.mapmory.shared.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.json.Json

class AndroidBackgroundSaveStore(context: Context, namespace: String) : BackgroundSaveStore {
    private val file = AtomicFile(File(context.filesDir, "pending-records-${namespace.hashCode()}.json"))
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun read(): List<PendingTripRecordSave> =
        if (!file.baseFile.exists()) emptyList()
        else json.decodeFromString(file.readFully().decodeToString())

    @Synchronized
    override fun write(saves: List<PendingTripRecordSave>) {
        val bytes = json.encodeToString(saves).encodeToByteArray()
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
