@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
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
    override suspend fun capturedAt(localId: String): String? {
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

        return suspendCoroutine { continuation ->
            val chunks = mutableListOf<ByteArray>()
            val options = PHAssetResourceRequestOptions().apply {
                networkAccessAllowed = true
            }
            PHAssetResourceManager.defaultManager().requestDataForAssetResource(
                resource,
                options,
                dataReceivedHandler = { data -> data?.let { chunks += it.toByteArray() } },
                completionHandler = { error ->
                    continuation.resume(
                        if (error == null) chunks.joinToByteArray().takeIf(ByteArray::isNotEmpty)
                        else null,
                    )
                },
            )
        }
    }
}

internal fun cacheIosPickerPhoto(data: NSData): String? {
    if (data.length == 0UL) return null
    val root = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
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

private fun List<ByteArray>.joinToByteArray(): ByteArray {
    val result = ByteArray(sumOf(ByteArray::size))
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(result, destinationOffset = offset)
        offset += chunk.size
    }
    return result
}

private const val IosPendingPhotoPrefix = "mapmory-cache://"
private const val IosPendingPhotoDirectory = "mapmory-pending-photos"
