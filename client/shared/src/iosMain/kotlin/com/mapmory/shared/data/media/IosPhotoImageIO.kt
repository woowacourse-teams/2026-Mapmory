@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.mapmory.shared.data.media

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.getBytes
import platform.ImageIO.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

// CFString pointers are not NSString keys when boxed into a Kotlin Map.
internal fun imageIoKey(key: CFStringRef?): String =
    CFBridgingRelease(CFRetain(requireNotNull(key))) as String

internal fun ByteArray.iosPhotoData(): NSData = if (isEmpty()) NSData() else usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}

internal fun NSData.photoBytes(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) bytes.usePinned { getBytes(it.addressOf(0), length) }
}

internal fun <T> NSData.withPhotoImageSource(block: (CGImageSourceRef) -> T): T? {
    val retained = CFBridgingRetain(this) ?: return null
    val source = try {
        CGImageSourceCreateWithData(retained.reinterpret(), null)
    } finally {
        CFRelease(retained)
    } ?: return null
    return try {
        block(source)
    } finally {
        CFRelease(source.reinterpret())
    }
}

internal fun NSData.photoProperties(): Map<*, *>? = withPhotoImageSource { source ->
    CGImageSourceCopyPropertiesAtIndex(source, CGImageSourceGetPrimaryImageIndex(source), null)
        ?.let { CFBridgingRelease(it) as? Map<*, *> }
}

/** Dimensions after applying the EXIF orientation, as used by the display JPEG. */
internal fun NSData.photoDimensions(): Pair<Int, Int>? {
    val properties = photoProperties() ?: return null
    val width = (properties[imageIoKey(kCGImagePropertyPixelWidth)] as? Number)?.toInt() ?: return null
    val height = (properties[imageIoKey(kCGImagePropertyPixelHeight)] as? Number)?.toInt() ?: return null
    if (width <= 0 || height <= 0) return null
    val orientation = (properties[imageIoKey(kCGImagePropertyOrientation)] as? Number)?.toInt()
    return if (orientation in 5..8) height to width else width to height
}

/** Generate an oriented JPEG without relying on an embedded thumbnail. Null size preserves pixels. */
internal fun NSData.photoDisplayJpeg(maxDimension: Int? = null): ByteArray? {
    val dimension = maxDimension ?: photoDimensions()?.let { maxOf(it.first, it.second) } ?: return null
    if (dimension <= 0) return null
    return withPhotoImageSource { source ->
        val options = mapOf(
            imageIoKey(kCGImageSourceCreateThumbnailFromImageAlways) to true,
            imageIoKey(kCGImageSourceCreateThumbnailWithTransform) to true,
            imageIoKey(kCGImageSourceThumbnailMaxPixelSize) to dimension,
        )
        val retainedOptions = CFBridgingRetain(options) ?: return@withPhotoImageSource null
        val thumbnail = try {
            CGImageSourceCreateThumbnailAtIndex(
                source, CGImageSourceGetPrimaryImageIndex(source), retainedOptions.reinterpret(),
            )
        } finally {
            CFRelease(retainedOptions)
        } ?: return@withPhotoImageSource null
        try {
            UIImageJPEGRepresentation(UIImage.imageWithCGImage(thumbnail), 0.85)?.photoBytes()
        } finally {
            CGImageRelease(thumbnail)
        }
    }
}
