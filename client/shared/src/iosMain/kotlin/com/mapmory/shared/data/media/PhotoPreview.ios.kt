@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.mapmory.shared.data.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.getBytes
import platform.Foundation.writeToFile
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImageJPEGRepresentation

class IosPhotoPreviewCache : PhotoPreviewCache {
    private val fileManager = NSFileManager.defaultManager
    private val directory: String? =
        NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
            .firstOrNull()
            ?.let { root -> "$root/$CacheDirectoryName" }

    override suspend fun read(objectKey: String): ByteArray? {
        val directory = directory ?: return null
        val path = "$directory/${objectKeyCacheFileName(objectKey)}"
        return NSData.dataWithContentsOfFile(path)?.toByteArray()?.takeIf(ByteArray::isNotEmpty)
    }

    override suspend fun write(objectKey: String, bytes: ByteArray) {
        val directory = directory ?: return
        if (objectKey.isBlank() || bytes.isEmpty() || bytes.size > MaxPreviewCacheEntryBytes) return
        fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        bytes.toNSData().writeToFile(
            path = "$directory/${objectKeyCacheFileName(objectKey)}",
            atomically = true,
        )
    }
}

internal actual fun createRemotePhotoPreview(originalBytes: ByteArray): ByteArray? {
    val retainedData = CFBridgingRetain(originalBytes.toNSData()) ?: return null
    val imageSource = CGImageSourceCreateWithData(retainedData.reinterpret(), null)
    CFRelease(retainedData)
    if (imageSource == null) return null

    val options = mapOf(
        kCGImageSourceCreateThumbnailFromImageAlways to true,
        kCGImageSourceCreateThumbnailWithTransform to true,
        kCGImageSourceThumbnailMaxPixelSize to PreviewSizePx,
    )
    val retainedOptions = CFBridgingRetain(options) ?: run {
        CFRelease(imageSource.reinterpret())
        return null
    }
    val thumbnail = CGImageSourceCreateThumbnailAtIndex(
        imageSource,
        0UL,
        retainedOptions.reinterpret(),
    )
    CFRelease(retainedOptions)
    CFRelease(imageSource.reinterpret())
    if (thumbnail == null) return null

    val image = platform.UIKit.UIImage.imageWithCGImage(thumbnail)
    val preview = UIImageJPEGRepresentation(image, PreviewJpegQuality)
    CGImageRelease(thumbnail)
    return preview?.toByteArray()
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

private fun NSData.toByteArray(): ByteArray {
    if (length == 0UL) return ByteArray(0)
    return ByteArray(length.toInt()).also { result ->
        result.usePinned { pinned -> getBytes(pinned.addressOf(0), length) }
    }
}

private const val CacheDirectoryName = "mapmory-photo-previews"
private const val MaxPreviewCacheEntryBytes = 5 * 1024 * 1024
private const val PreviewSizePx = 1280
private const val PreviewJpegQuality = 0.85
