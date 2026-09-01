package com.mapmory.shared.presentation.triprecord.thumbnail

import com.mapmory.shared.domain.model.TripRecordSummary

/** 목록 UI가 본문과 독립적으로 썸네일을 채울 때 사용하는 경계다. */
fun interface TripRecordThumbnailLoader {
    suspend fun load(record: TripRecordSummary): TripRecordThumbnailLoadResult
}

sealed interface TripRecordThumbnailLoadResult {
    data class Success(val previewBytes: ByteArray) : TripRecordThumbnailLoadResult

    data object UrlExpired : TripRecordThumbnailLoadResult

    data object Unavailable : TripRecordThumbnailLoadResult
}
