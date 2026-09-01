package com.mapmory.shared.presentation.triprecord.state

import com.mapmory.shared.domain.TripRecord
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.presentation.photo.SelectedPhoto

/** 화면에 필요한 여행 기록 표현. 도메인 모델과 플랫폼 사진 데이터를 UI 경계에서 분리한다. */
data class TripRecordItemUiState(
    val id: Long,
    val title: String,
    val content: String,
    val startDate: String?,
    val endDate: String?,
    val locationName: String,
    val photos: List<TripRecordPhotoUiState>,
)

data class TripRecordPhotoUiState(
    val id: String,
    val displayName: String,
    val previewBytes: PhotoPreviewBytes?,
    val sortOrder: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: String? = null,
    val originalBytes: PhotoPreviewBytes? = null,
)

/** ByteArray의 변경 가능성을 UI 상태 밖으로 숨기고 생성 시점에 방어적으로 복사한다. */
class PhotoPreviewBytes private constructor(
    private val value: ByteArray,
) {
    internal fun bytesForDecoding(): ByteArray = value

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is PhotoPreviewBytes -> false
        else -> value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    companion object {
        fun from(bytes: ByteArray?): PhotoPreviewBytes? =
            bytes?.let { PhotoPreviewBytes(it.copyOf()) }
    }
}

fun SelectedPhoto.toTripRecordPhotoUiState(sortOrder: Int): TripRecordPhotoUiState =
    TripRecordPhotoUiState(
        id = id,
        displayName = displayName,
        previewBytes = PhotoPreviewBytes.from(previewBytes),
        sortOrder = sortOrder,
        latitude = latitude,
        longitude = longitude,
        capturedAt = capturedAt,
        originalBytes = PhotoPreviewBytes.from(originalBytes),
    )

fun TripRecordData.toTripRecordItemUiState(
    locationName: String = "여행지",
): TripRecordItemUiState = TripRecordItemUiState(
    id = id,
    title = title,
    content = content,
    startDate = startDate,
    endDate = endDate,
    locationName = locationName,
    photos = media
        .sortedBy { it.sortOrder }
        .map { media ->
            TripRecordPhotoUiState(
                id = media.objectKey,
                displayName = media.objectKey.substringAfterLast('/'),
                previewBytes = PhotoPreviewBytes.from(media.previewBytes ?: media.originalBytes),
                sortOrder = media.sortOrder,
                latitude = media.latitude,
                longitude = media.longitude,
                capturedAt = media.capturedAt,
                originalBytes = null,
            )
        },
)

fun TripRecordSummary.toTripRecordItemUiState(
    locationName: String = regionName ?: "여행지",
): TripRecordItemUiState = TripRecordItemUiState(
    id = id,
    title = title,
    content = content,
    startDate = startDate,
    endDate = endDate,
    locationName = locationName,
    photos = thumbnailPreviewBytes?.let { bytes ->
        listOf(
            TripRecordPhotoUiState(
                id = "thumbnail-$id",
                displayName = "thumbnail-$id",
                previewBytes = PhotoPreviewBytes.from(bytes),
                sortOrder = 0,
            ),
        )
    } ?: media
        .sortedBy { it.sortOrder }
        .mapIndexed { index, media ->
            TripRecordPhotoUiState(
                id = media.objectKey,
                displayName = media.objectKey.substringAfterLast('/'),
                previewBytes = if (index == 0) {
                    PhotoPreviewBytes.from(media.previewBytes ?: media.originalBytes)
                } else {
                    null
                },
                sortOrder = media.sortOrder,
                latitude = media.latitude,
                longitude = media.longitude,
                capturedAt = media.capturedAt,
                originalBytes = null,
            )
        },
)

internal fun TripRecord.toTripRecordItemUiState(
    photos: List<TripRecordPhotoUiState>,
): TripRecordItemUiState = TripRecordItemUiState(
    id = id,
    title = tripRecordTitle,
    content = tripRecordDescription.orEmpty(),
    startDate = startTripDate?.toString(),
    endDate = endTripDate?.toString(),
    locationName = location,
    photos = photos.sortedBy { it.sortOrder },
)
