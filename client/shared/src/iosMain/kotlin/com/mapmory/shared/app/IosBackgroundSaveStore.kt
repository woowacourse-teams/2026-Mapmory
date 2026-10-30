@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.mapmory.shared.app

import kotlinx.serialization.json.Json
import kotlinx.cinterop.*
import platform.Foundation.*

class IosBackgroundSaveStore(namespace: String) : BackgroundSaveStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val path: String

    init {
        val root = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        check(NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null))
        path = "$root/pending-records-${namespace.hashCode()}.json"
    }

    override fun read(): List<PendingTripRecordSave> {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return emptyList()
        val payload = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
            ?: error("저장 대기 중인 기록을 읽지 못했어요.")
        return json.decodeFromString(payload)
    }

    override fun write(saves: List<PendingTripRecordSave>) {
        val bytes = json.encodeToString(saves).encodeToByteArray()
        check(bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true)
        }) {
            "기록을 기기에 저장하지 못했어요."
        }
    }
}
