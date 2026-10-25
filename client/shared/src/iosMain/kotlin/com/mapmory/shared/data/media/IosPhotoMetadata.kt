@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.mapmory.shared.data.media

import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.ImageIO.*

/** Read the original metadata before generating a JPEG preview (which drops EXIF). */
internal fun NSData.originalPhotoDate(): String? {
    val retained = CFBridgingRetain(this) ?: return null
    val source = CGImageSourceCreateWithData(retained.reinterpret(), null)
    CFRelease(retained)
    if (source == null) return null
    val properties = CGImageSourceCopyPropertiesAtIndex(source, CGImageSourceGetPrimaryImageIndex(source), null)
    CFRelease(source.reinterpret())
    if (properties == null) return null
    val map = platform.Foundation.CFBridgingRelease(properties) as? Map<*, *>
    val exif = map?.get(kCGImagePropertyExifDictionary) as? Map<*, *>
    val tiff = map?.get(kCGImagePropertyTIFFDictionary) as? Map<*, *>
    val raw = (exif?.get(kCGImagePropertyExifDateTimeOriginal)
        ?: exif?.get(kCGImagePropertyExifDateTimeDigitized)
        ?: tiff?.get(kCGImagePropertyTIFFDateTime)) as? String ?: return null
    return Regex("""^(\d{4}):(\d{2}):(\d{2})[ T].*""").matchEntire(raw)
        ?.destructured?.let { (year, month, day) -> "$year.$month.$day" }
}
