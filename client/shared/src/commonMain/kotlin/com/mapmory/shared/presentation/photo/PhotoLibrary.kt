package com.mapmory.shared.presentation.photo

import androidx.compose.runtime.Composable
import com.mapmory.shared.domain.model.Location
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

data class SelectedPhoto(
    val id: String,
    val displayName: String,
    val previewBytes: ByteArray?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: String? = null,
    val originalBytes: ByteArray? = null,
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
    val recommendForLocation: (Location, String?) -> Unit,
    val recommendForLocationInDateRange: (
        Location,
        String?,
        PhotoRecommendationDateRange,
    ) -> Unit = { location, parentName, _ -> recommendForLocation(location, parentName) },
    val loadNextRecommendationPage: () -> Unit = {},
    val prepareForAdding: (
        photos: List<SelectedPhoto>,
        onReady: (List<SelectedPhoto>) -> Unit,
    ) -> Unit = { photos, onReady -> onReady(photos) },
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
): PhotoRecommendationDateRange? {
    val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
    val end = endDate
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: start
    if (end < start) return null

    val until = end.plus(1, DateTimeUnit.DAY)
    return PhotoRecommendationDateRange(
        fromInclusiveMillis = start.atStartOfDayIn(timeZone).toEpochMilliseconds(),
        untilExclusiveMillis = until.atStartOfDayIn(timeZone).toEpochMilliseconds(),
    )
}
