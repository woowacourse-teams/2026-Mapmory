package com.mapmory.shared.presentation.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPhotoLibraryActions(
    onPhotosPicked: (List<SelectedPhoto>) -> Unit,
    onPhotosRecommended: (PhotoRecommendationPage) -> Unit,
    onMessage: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadingProgressChanged: (PhotoLoadingProgress) -> Unit,
    onRecommendationLoadingChanged: (Boolean) -> Unit,
): PhotoLibraryActions = remember(onMessage, onLoadingChanged, onLoadingProgressChanged) {
    PhotoLibraryActions(
        pickFromGallery = { onMessage("사진 선택은 Android와 iOS 앱에서 사용할 수 있어요.") },
        recommendForLocation = { _, _ -> },
        recommendationsAvailable = false,
    )
}
