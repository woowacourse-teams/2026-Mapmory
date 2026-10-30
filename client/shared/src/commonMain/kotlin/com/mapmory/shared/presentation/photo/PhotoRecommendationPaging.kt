package com.mapmory.shared.presentation.photo

import com.mapmory.shared.domain.model.TripRecordPhotoRules

internal data class PhotoRecommendationPagingState(
    val generation: Int? = null,
    val photos: List<SelectedPhoto> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val pageIndex: Int = 0,
    val hasMore: Boolean = false,
    val maxSelectionCount: Int = TripRecordPhotoRules.MaxPhotosPerRecord,
) {
    init {
        require(maxSelectionCount >= 0)
    }
}

internal data class RecommendationLoadKey(
    val generation: Int,
    val visibleCount: Int,
)

internal const val PhotoRecommendationPageSize = 24

internal fun PhotoRecommendationPagingState.accept(
    page: PhotoRecommendationPage,
    autoSelectNewPhotos: Boolean = true,
    preselectedIds: Set<String> = emptySet(),
): PhotoRecommendationPagingState? {
    if (generation != null && generation != page.generation) return null

    val existingIds = photos.mapTo(mutableSetOf(), SelectedPhoto::id)
    val incoming = page.photos
        .filterNot { photo -> photo.id in existingIds }
        .distinctBy(SelectedPhoto::id)
    val isFirstPage = generation == null
    val previews = page.photos.filter { it.previewBytes != null }.associateBy(SelectedPhoto::id)
    val refreshedPhotos = photos.map { previews[it.id] ?: it }

    if (!isFirstPage && incoming.isEmpty()) {
        return copy(photos = refreshedPhotos, hasMore = page.hasMore)
    }

    val nextPhotos = refreshedPhotos + incoming
    val remainingSlots = (maxSelectionCount - selectedIds.size).coerceAtLeast(0)
    val matchingPreselectedIds = incoming
        .asSequence()
        .map(SelectedPhoto::id)
        .filter(preselectedIds::contains)
        .filterNot(selectedIds::contains)
        .take(remainingSlots)
        .toSet()
    val selectedFromIncoming = if (autoSelectNewPhotos) {
        incoming
            .asSequence()
            .map(SelectedPhoto::id)
            .filterNot(selectedIds::contains)
            .filterNot(matchingPreselectedIds::contains)
            .take((remainingSlots - matchingPreselectedIds.size).coerceAtLeast(0))
            .toSet()
    } else {
        emptySet()
    }
    return copy(
        generation = page.generation,
        photos = nextPhotos,
        selectedIds = selectedIds + matchingPreselectedIds + selectedFromIncoming,
        pageIndex = if (isFirstPage) 0 else pageIndex + 1,
        hasMore = page.hasMore,
    )
}

internal fun PhotoRecommendationPagingState.selectAllLoadedPhotos(): PhotoRecommendationPagingState {
    if (selectedIds.size >= maxSelectionCount) return this
    val idsToAdd = photos
        .asSequence()
        .map(SelectedPhoto::id)
        .filterNot(selectedIds::contains)
        .distinct()
        .take((maxSelectionCount - selectedIds.size).coerceAtLeast(0))
        .toSet()
    return if (idsToAdd.isEmpty()) this else copy(selectedIds = selectedIds + idsToAdd)
}

internal fun PhotoRecommendationPagingState.toggleSelection(
    photoId: String,
): PhotoRecommendationPagingState {
    if (photos.none { it.id == photoId }) return this
    val nextSelectedIds = if (photoId in selectedIds) {
        selectedIds - photoId
    } else {
        if (selectedIds.size >= maxSelectionCount) return this
        selectedIds + photoId
    }
    return copy(selectedIds = nextSelectedIds)
}

internal fun PhotoRecommendationPagingState.setSelection(
    photoId: String,
    selected: Boolean,
): PhotoRecommendationPagingState {
    if (photos.none { it.id == photoId }) return this
    if (selected == (photoId in selectedIds)) return this
    if (selected && selectedIds.size >= maxSelectionCount) return this
    return copy(
        selectedIds = if (selected) selectedIds + photoId else selectedIds - photoId,
    )
}

internal fun shouldLoadNextRecommendationPage(
    isAtBottom: Boolean,
    isLoading: Boolean,
    hasMore: Boolean,
    lastTriggerKey: RecommendationLoadKey?,
    currentKey: RecommendationLoadKey,
): Boolean = isAtBottom && !isLoading && hasMore && lastTriggerKey != currentKey
