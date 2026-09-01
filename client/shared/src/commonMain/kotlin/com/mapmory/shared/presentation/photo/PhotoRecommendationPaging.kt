package com.mapmory.shared.presentation.photo

internal data class PhotoRecommendationPagingState(
    val generation: Int? = null,
    val photos: List<SelectedPhoto> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val pageIndex: Int = 0,
    val hasMore: Boolean = false,
)

internal data class RecommendationLoadKey(
    val generation: Int,
    val visibleCount: Int,
)

internal const val PhotoRecommendationPageSize = 24

internal fun PhotoRecommendationPagingState.accept(
    page: PhotoRecommendationPage,
): PhotoRecommendationPagingState? {
    if (generation != null && generation != page.generation) return null

    val existingIds = photos.mapTo(mutableSetOf(), SelectedPhoto::id)
    val incoming = page.photos
        .filterNot { photo -> photo.id in existingIds }
        .distinctBy(SelectedPhoto::id)
    val isFirstPage = generation == null

    if (!isFirstPage && incoming.isEmpty()) {
        return copy(hasMore = page.hasMore)
    }

    val nextPhotos = if (isFirstPage) incoming else photos + incoming
    return copy(
        generation = page.generation,
        photos = nextPhotos,
        selectedIds = if (isFirstPage) {
            incoming.mapTo(mutableSetOf(), SelectedPhoto::id)
        } else {
            selectedIds + incoming.map(SelectedPhoto::id)
        },
        pageIndex = if (isFirstPage) 0 else pageIndex + 1,
        hasMore = page.hasMore,
    )
}

internal fun PhotoRecommendationPagingState.toggleSelection(
    photoId: String,
): PhotoRecommendationPagingState {
    if (photos.none { it.id == photoId }) return this
    val nextSelectedIds = if (photoId in selectedIds) {
        selectedIds - photoId
    } else {
        selectedIds + photoId
    }
    return copy(selectedIds = nextSelectedIds)
}

internal fun shouldLoadNextRecommendationPage(
    isAtBottom: Boolean,
    isLoading: Boolean,
    hasMore: Boolean,
    lastTriggerKey: RecommendationLoadKey?,
    currentKey: RecommendationLoadKey,
): Boolean = isAtBottom && !isLoading && hasMore && lastTriggerKey != currentKey
