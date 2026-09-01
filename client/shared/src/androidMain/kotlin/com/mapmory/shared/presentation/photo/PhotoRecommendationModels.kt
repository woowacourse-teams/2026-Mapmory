package com.mapmory.shared.presentation.photo

import com.mapmory.shared.data.local.photo.PhotoMetadataEntity

internal data class AndroidRecommendationSession(
    val generation: Int,
    val candidates: List<PhotoMetadataEntity>,
    val syncResult: PhotoMetadataSyncResult,
    val boundaryLoadMillis: Long,
    val syncMillis: Long,
    val regionFilterMillis: Long,
    val discoveryMillis: Long,
    val nextIndex: Int = 0,
) {
    val hasMore: Boolean
        get() = nextIndex < candidates.size
}

internal data class RecommendationPreviewPage(
    val generation: Int,
    val photos: List<SelectedPhoto>,
    val nextIndex: Int,
    val hasMore: Boolean,
) {
    fun asPublicPage(): PhotoRecommendationPage = PhotoRecommendationPage(
        generation = generation,
        photos = photos,
        hasMore = hasMore,
    )
}
