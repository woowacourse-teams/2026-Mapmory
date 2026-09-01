package com.mapmory.shared.presentation.photo

import androidx.compose.runtime.Composable
import com.mapmory.shared.domain.model.Location

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

data class PhotoLibraryActions(
    val pickFromGallery: () -> Unit,
    val recommendForLocation: (Location, String?) -> Unit,
    val loadNextRecommendationPage: () -> Unit = {},
    val prepareForAdding: (
        photos: List<SelectedPhoto>,
        onReady: (List<SelectedPhoto>) -> Unit,
    ) -> Unit = { photos, onReady -> onReady(photos) },
    val recommendationsAvailable: Boolean = true,
    val cancelRecommendation: () -> Unit = {},
)

typealias PhotoLibraryActionsFactory = @Composable (
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (PhotoRecommendationPage) -> Unit,
    onMessage: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadingProgressChanged: (PhotoLoadingProgress) -> Unit,
    onRecommendationLoadingChanged: (Boolean) -> Unit,
) -> PhotoLibraryActions

@Composable
expect fun rememberPhotoLibraryActions(
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (PhotoRecommendationPage) -> Unit,
    onMessage: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadingProgressChanged: (PhotoLoadingProgress) -> Unit,
    onRecommendationLoadingChanged: (Boolean) -> Unit,
): PhotoLibraryActions

internal fun mergeSelectedPhotos(
    existing: List<SelectedPhoto>,
    incoming: List<SelectedPhoto>,
): List<SelectedPhoto> = (existing + incoming).distinctBy(SelectedPhoto::id)
