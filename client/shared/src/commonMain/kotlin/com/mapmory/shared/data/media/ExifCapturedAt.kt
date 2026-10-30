package com.mapmory.shared.data.media

import kotlinx.datetime.LocalDateTime

/** EXIF DateTimeOriginal 전체를 검증한 뒤 시간대 변환 없이 촬영 날짜를 정규화한다. */
internal fun normalizeExifCapturedAt(value: String?): String? {
    if (value == null || !ExifDateTimePattern.matches(value)) return null
    if (value.take(4) == "0000") return null
    val isoDateTime = value.take(10).replace(':', '-') + "T" + value.substring(11)
    return runCatching { LocalDateTime.parse(isoDateTime).date.toString() }.getOrNull()
}

private val ExifDateTimePattern = Regex("[0-9]{4}:[0-9]{2}:[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")
