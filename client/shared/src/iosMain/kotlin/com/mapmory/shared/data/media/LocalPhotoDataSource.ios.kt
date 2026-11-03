@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.getBytes
import platform.Foundation.writeToFile
import platform.Photos.PHAsset
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypePhoto
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosLocalPhotoDataSource : LocalPhotoDataSource {
    override suspend fun release(localId: String) {
        if (localId.startsWith(IosPendingPhotoPrefix)) {
            val root = NSSearchPathForDirectoriesInDomains(
                NSApplicationSupportDirectory, NSUserDomainMask, true,
            ).firstOrNull() as? String ?: return
            val directory = "$root/$IosPendingPhotoDirectory/"
            val path = localId.removePrefix(IosPendingPhotoPrefix)
            // Only remove a direct child created by cacheIosPickerPhoto.
            val filename = path.removePrefix(directory)
            if (path.startsWith(directory) && filename.isNotEmpty() &&
                filename != "." && filename != ".." && '/' !in filename
            ) {
                withContext(Dispatchers.Default) {
                    NSFileManager.defaultManager.removeItemAtPath(path, null)
                }
            }
            return
        }
        val root = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        val directory = "$root/mapmory-upload-originals"
        val prefix = "$localId:".encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        val manager = NSFileManager.defaultManager
        manager.contentsOfDirectoryAtPath(directory, null)?.filterIsInstance<String>()
            ?.filter { it.startsWith(prefix) }?.forEach { manager.removeItemAtPath("$directory/$it", null) }
    }

    override suspend fun capturedAt(localId: String): String? {
        if (localId.startsWith(IosPendingPhotoPrefix)) {
            return NSData.dataWithContentsOfFile(localId.removePrefix(IosPendingPhotoPrefix))?.originalPhotoDate()
        }
        val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localId), null).firstObject as? PHAsset
            ?: return null
        return asset.creationDate?.let { date ->
            platform.Foundation.NSDateFormatter().run {
                dateFormat = "yyyy.MM.dd"
                stringFromDate(date)
            }
        }
    }

    override suspend fun read(localId: String): ByteArray? {
        if (localId.startsWith(IosPendingPhotoPrefix)) {
            val path = localId.removePrefix(IosPendingPhotoPrefix)
            return NSData.dataWithContentsOfFile(path)
                ?.toByteArray()
                ?.takeIf(ByteArray::isNotEmpty)
        }

        val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localId), null)
            .firstObject as? PHAsset
            ?: return null
        val resources = PHAssetResource.assetResourcesForAsset(asset)
            .filterIsInstance<PHAssetResource>()
        val resource = resources.firstOrNull { it.type == PHAssetResourceTypePhoto }
            ?: resources.firstOrNull()
            ?: return null

        val root = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        val directory = "$root/mapmory-upload-originals"
        val manager = NSFileManager.defaultManager
        if (!manager.createDirectoryAtPath(directory, true, null, null)) return null
        val version = asset.modificationDate?.timeIntervalSinceReferenceDate ?: 0.0
        val key = "$localId:$version".encodeToByteArray().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        val path = "$directory/$key"
        if (!manager.fileExistsAtPath(path)) {
            val temporary = "$directory/${NSUUID().UUIDString}.part"
            val success = suspendCoroutine<Boolean> { continuation ->
                PHAssetResourceManager.defaultManager().writeDataForAssetResource(
                    resource,
                    toFile = NSURL.fileURLWithPath(temporary),
                    options = PHAssetResourceRequestOptions().apply { networkAccessAllowed = true },
                ) { error -> continuation.resume(error == null) }
            }
            if (!success) {
                manager.removeItemAtPath(temporary, null)
                return null
            }
            if (!manager.moveItemAtPath(temporary, path, null)) {
                manager.removeItemAtPath(temporary, null)
                if (!manager.fileExistsAtPath(path)) return null
            }
        }
        return withContext(Dispatchers.Default) {
            NSData.dataWithContentsOfFile(path)?.toByteArray()?.takeIf(ByteArray::isNotEmpty)
        }
    }
}

internal fun cacheIosPickerPhoto(data: NSData): String? {
    if (data.length == 0UL) return null
    val root = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() ?: return null
    val directory = "$root/$IosPendingPhotoDirectory"
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    ) {
        return null
    }
    val path = "$directory/${NSUUID().UUIDString}"
    return if (data.writeToFile(path = path, atomically = true)) {
        "$IosPendingPhotoPrefix$path"
    } else {
        null
    }
}

internal fun isIosLocalPhotoAvailable(localId: String): Boolean =
    if (localId.startsWith(IosPendingPhotoPrefix)) {
        NSFileManager.defaultManager.fileExistsAtPath(localId.removePrefix(IosPendingPhotoPrefix))
    } else {
        PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localId), null).firstObject != null
    }

private fun NSData.toByteArray(): ByteArray {
    if (length == 0UL) return ByteArray(0)
    return ByteArray(length.toInt()).also { bytes ->
        bytes.usePinned { pinned -> getBytes(pinned.addressOf(0), length) }
    }
}

private const val IosPendingPhotoPrefix = "mapmory-cache://"
private const val IosPendingPhotoDirectory = "mapmory-pending-photos"
