@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import kotlinx.coroutines.ensureActive
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Photos.PHAsset
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypePhoto
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Previewing a recommendation does not acquire ownership of an upload original. */
internal suspend fun loadIosAssetPhotoPreview(asset: PHAsset, maxDimension: Int): ByteArray? {
    val resources = PHAssetResource.assetResourcesForAsset(asset).filterIsInstance<PHAssetResource>()
    val resource = resources.firstOrNull { it.type == PHAssetResourceTypePhoto }
        ?: resources.firstOrNull() ?: return null
    return withIosTemporaryPhotoFile(
        write = { path, complete ->
            PHAssetResourceManager.defaultManager().writeDataForAssetResource(
                resource,
                toFile = NSURL.fileURLWithPath(path),
                options = PHAssetResourceRequestOptions().apply { networkAccessAllowed = true },
            ) { error -> complete(error == null) }
        },
        read = { path -> iosPhotoFileDisplayJpeg(path, maxDimension) },
    )
}

/**
 * The writer must finish before deleting its destination: PhotoKit's file-writing API cannot be
 * cancelled. On coroutine cancellation we wait for that callback, skip decoding, then clean up.
 * Each request owns a unique file and never removes the cache used by an in-flight upload.
 */
internal suspend fun <T> withIosTemporaryPhotoFile(
    write: (path: String, complete: (Boolean) -> Unit) -> Unit,
    read: suspend (path: String) -> T,
): T? {
    coroutineContext.ensureActive()
    val path = "${NSTemporaryDirectory()}mapmory-photo-preview-${NSUUID().UUIDString}"
    try {
        val success = suspendCoroutine<Boolean> { continuation ->
            write(path) { continuation.resume(it) }
        }
        coroutineContext.ensureActive()
        return if (success) read(path) else null
    } finally {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
