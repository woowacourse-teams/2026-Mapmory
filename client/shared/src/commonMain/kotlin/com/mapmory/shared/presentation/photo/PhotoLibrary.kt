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

data class PhotoLibraryActions(
    val pickFromGallery: () -> Unit,
    val recommendForLocation: (Location, String?) -> Unit,
    val prepareForAdding: (
        photos: List<SelectedPhoto>,
        onReady: (List<SelectedPhoto>) -> Unit,
    ) -> Unit = { photos, onReady -> onReady(photos) },
    val recommendationsAvailable: Boolean = true,
)

typealias PhotoLibraryActionsFactory = @Composable (
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (List<SelectedPhoto>) -> Unit,
    onMessage: (String) -> Unit,
) -> PhotoLibraryActions

@Composable
expect fun rememberPhotoLibraryActions(
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (List<SelectedPhoto>) -> Unit,
    onMessage: (String) -> Unit,
): PhotoLibraryActions

internal fun mergeSelectedPhotos(
    existing: List<SelectedPhoto>,
    incoming: List<SelectedPhoto>,
): List<SelectedPhoto> = (existing + incoming).distinctBy(SelectedPhoto::id)
