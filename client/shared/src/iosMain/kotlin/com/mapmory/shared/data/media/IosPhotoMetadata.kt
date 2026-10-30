@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mapmory.shared.data.media

import platform.Foundation.NSData
import platform.ImageIO.kCGImagePropertyExifDictionary
import platform.ImageIO.kCGImagePropertyTIFFDictionary
import platform.ImageIO.kCGImagePropertyExifDateTimeOriginal
import platform.ImageIO.kCGImagePropertyExifDateTimeDigitized
import platform.ImageIO.kCGImagePropertyTIFFDateTime

/** Read the original metadata before generating a JPEG preview (which drops EXIF). */
internal fun NSData.originalPhotoDate(): String? {
    val properties = photoProperties() ?: return null
    val exif = properties[imageIoKey(kCGImagePropertyExifDictionary)] as? Map<*, *>
    val tiff = properties[imageIoKey(kCGImagePropertyTIFFDictionary)] as? Map<*, *>
    val raw = (exif?.get(imageIoKey(kCGImagePropertyExifDateTimeOriginal))
        ?: exif?.get(imageIoKey(kCGImagePropertyExifDateTimeDigitized))
        ?: tiff?.get(imageIoKey(kCGImagePropertyTIFFDateTime))) as? String ?: return null
    return Regex("""^(\d{4}):(\d{2}):(\d{2})[ T].*""").matchEntire(raw)
        ?.destructured?.let { (year, month, day) -> "$year.$month.$day" }
}
