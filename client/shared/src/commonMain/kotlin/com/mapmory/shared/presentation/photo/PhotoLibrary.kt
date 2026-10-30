package com.mapmory.shared.presentation.photo

import androidx.compose.runtime.Composable
import com.mapmory.shared.domain.model.Location
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class SelectedPhoto(
    val id: String,
    val displayName: String,
    val previewBytes: ByteArray?,
    /** 확대 화면에서만 사용하는 원본 로컬 URI. 목록에서는 [previewBytes]를 사용한다. */
    val fullResolutionUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: String? = null,
    val originalBytes: ByteArray? = null,
    val previewUri: String? = null,
)

data class PhotoLoadingProgress(
    val processed: Int,
    val total: Int,
) {
    val percentage: Int?
        get() = total.takeIf { it > 0 }
            ?.let { (processed * 100 / it).coerceIn(0, 100) }
}

data class PhotoRecommendationPage(
    val generation: Int,
    val photos: List<SelectedPhoto>,
    val hasMore: Boolean,
    val totalMatchingCount: Int? = null,
    val excludedCount: Int = 0,
)

data class PhotoRecommendationDateRange(
    val fromInclusiveMillis: Long,
    val untilExclusiveMillis: Long,
)

enum class PhotoLibraryPermissionIssue {
    LIMITED,
    DENIED,
    RESTRICTED,
}

data class PhotoLibraryActions(
    val pickFromGallery: () -> Unit,
    val setExcludedPhotoIds: (Set<String>) -> Unit = {},
    val recommendForLocation: (Location, String?) -> Unit,
    val recommendForLocationInDateRange: (
        Location,
        String?,
        PhotoRecommendationDateRange,
    ) -> Unit = { location, parentName, _ -> recommendForLocation(location, parentName) },
    val loadNextRecommendationPage: () -> Unit = {},
    /** 선택할 사진의 메타데이터를 즉시 전달하고, 미리보기는 이후 페이지 단위로 채운다. */
    val loadRecommendationPagesForSelection: (Int) -> Unit = {},
    val prepareForAdding: (
        photos: List<SelectedPhoto>,
        onReady: (List<SelectedPhoto>) -> Unit,
    ) -> Unit = { photos, onReady -> onReady(photos) },
    val loadFullResolutionPreview: (
        photo: SelectedPhoto,
        onReady: (ByteArray?) -> Unit,
    ) -> Unit = { photo, onReady -> onReady(photo.originalBytes) },
    val cancelFullResolutionPreview: () -> Unit = {},
    val recommendationsAvailable: Boolean = true,
    val cancelRecommendation: () -> Unit = {},
    val openAppSettings: () -> Unit = {},
)

typealias PhotoLibraryActionsFactory = @Composable (
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (PhotoRecommendationPage) -> Unit,
    onMessage: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadingProgressChanged: (PhotoLoadingProgress) -> Unit,
    onRecommendationLoadingChanged: (Boolean) -> Unit,
    onPermissionRequired: (PhotoLibraryPermissionIssue) -> Unit,
) -> PhotoLibraryActions

@Composable
expect fun rememberPhotoLibraryActions(
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (PhotoRecommendationPage) -> Unit,
    onMessage: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadingProgressChanged: (PhotoLoadingProgress) -> Unit,
    onRecommendationLoadingChanged: (Boolean) -> Unit,
    onPermissionRequired: (PhotoLibraryPermissionIssue) -> Unit,
): PhotoLibraryActions

internal fun mergeSelectedPhotos(
    existing: List<SelectedPhoto>,
    incoming: List<SelectedPhoto>,
): List<SelectedPhoto> = (existing + incoming).distinctBy(SelectedPhoto::id)

internal fun photoRecommendationDateRange(
    startDate: String,
    endDate: String?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.now().toLocalDateTime(timeZone).date,
): PhotoRecommendationDateRange? {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
    val end = if (endDate.isNullOrBlank()) {
        today
    } else {
        runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return null
    }
    if (end < start) return null

    val until = end.plus(1, DateTimeUnit.DAY)
    return PhotoRecommendationDateRange(
        fromInclusiveMillis = start.atStartOfDayIn(timeZone).toEpochMilliseconds(),
        untilExclusiveMillis = until.atStartOfDayIn(timeZone).toEpochMilliseconds(),
    )
}
