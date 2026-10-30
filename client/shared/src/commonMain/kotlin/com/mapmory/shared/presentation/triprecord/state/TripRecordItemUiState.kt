package com.mapmory.shared.presentation.triprecord.state

import com.mapmory.shared.domain.TripRecord
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.model.Tag
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
    val tags: List<Tag> = emptyList(),
    val photoCount: Int? = photos.size,
)

data class TripRecordPhotoUiState(
    val id: String,
    /** 기기 사진첩의 경량 식별자. 편집 시 기존 선택 표시용이며 원본 바이트를 보관하지 않는다. */
    val localPhotoId: String? = null,
    val displayName: String,
    val previewBytes: PhotoPreviewBytes?,
    val previewUri: String? = null,
    /** 확대 화면에서만 사용하는 서버 원본 URL. */
    val fullResolutionUri: String? = null,
    val sortOrder: Int,
    val isUploaded: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: String? = null,
    val originalBytes: PhotoPreviewBytes? = null,
)

/** Android 원본은 복사·다운로드 없이 읽는다. iOS Photos 식별자는 URI가 아니므로 제외한다. */
internal val TripRecordPhotoUiState.localOriginalUri: String?
    get() = localPhotoId?.takeIf { it.startsWith("content://") || it.startsWith("file://") }

/** 사진 디코딩 전용 바이트. 생성 이후 배열을 변경하지 않는 소유권 규칙으로 불필요한 복사를 피한다. */
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
            bytes?.let(::PhotoPreviewBytes)
    }
}

fun SelectedPhoto.toTripRecordPhotoUiState(sortOrder: Int): TripRecordPhotoUiState =
    TripRecordPhotoUiState(
        id = id,
        localPhotoId = id,
        displayName = displayName,
        previewBytes = PhotoPreviewBytes.from(previewBytes),
        previewUri = previewUri,
        fullResolutionUri = fullResolutionUri,
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
    tags = tags,
    photos = media
        .sortedBy { it.sortOrder }
        .map { media ->
            TripRecordPhotoUiState(
                id = media.objectKey,
                localPhotoId = media.localPreviewKey,
                displayName = media.objectKey.substringAfterLast('/'),
                previewBytes = PhotoPreviewBytes.from(media.previewBytes ?: media.originalBytes),
                previewUri = media.previewUri,
                fullResolutionUri = media.url,
                sortOrder = media.sortOrder,
                isUploaded = true,
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
    tags = tags,
    photoCount = photoCount ?: media.size.takeIf { it > 0 },
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
                localPhotoId = media.localPreviewKey,
                displayName = media.objectKey.substringAfterLast('/'),
                previewBytes = if (index == 0) {
                    PhotoPreviewBytes.from(media.previewBytes ?: media.originalBytes)
                } else {
                    null
                },
                previewUri = if (index == 0) media.previewUri else null,
                fullResolutionUri = media.url,
                sortOrder = media.sortOrder,
                isUploaded = true,
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
    startDate = startTripDate.toString(),
    endDate = endTripDate?.toString(),
    locationName = location,
    photos = photos.sortedBy { it.sortOrder },
)
