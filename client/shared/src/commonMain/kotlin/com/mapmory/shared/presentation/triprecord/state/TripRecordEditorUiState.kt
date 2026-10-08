package com.mapmory.shared.presentation.triprecord.state

import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceReference
import com.mapmory.shared.domain.model.Tag

data class TripRecordEditorUiState(
    val recordId: Long? = null,
    val selectedLocation: Location? = null,
    val selectedPlace: PlaceReference? = null,
    val isPlaceSearchAvailable: Boolean = false,
    val placeSearchResults: List<PlaceCandidate> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val hasSearchedPlaces: Boolean = false,
    val placeSearchErrorMessage: String? = null,
    val isSelectingPlace: Boolean = false,
    val placeSelectionErrorMessage: String? = null,
    val manualRegionRequired: Boolean = false,
    val title: String = "",
    val content: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val mediaObjectKeys: List<String> = emptyList(),
    val selectedPhotos: List<TripRecordPhotoUiState> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val pendingTagNames: List<String> = emptyList(),
    val selectedPendingTagNames: Set<String> = emptySet(),
    val tagInput: String = "",
    val isTagsLoading: Boolean = false,
    val tagErrorMessage: String? = null,
    val isDirty: Boolean = false,
    val dirtyFields: Set<TripRecordEditorErrorTarget> = emptySet(),
    val isSaving: Boolean = false,
    val isPhotoLoading: Boolean = false,
    val fieldErrors: Map<TripRecordEditorErrorTarget, String> = emptyMap(),
    val generalErrorMessage: String? = null,
) {
    val errorMessage: String?
        get() = generalErrorMessage ?: fieldErrors.values.firstOrNull()

    val errorTarget: TripRecordEditorErrorTarget?
        get() = if (generalErrorMessage != null) {
            TripRecordEditorErrorTarget.GENERAL
        } else {
            fieldErrors.keys.firstOrNull()
        }

    val isSaveEnabled: Boolean
        get() = !isSaving

    val selectedTagCount: Int
        get() = selectedTagIds.size + selectedPendingTagNames.size

    fun isFieldDirty(target: TripRecordEditorErrorTarget): Boolean = target in dirtyFields
}

enum class TripRecordEditorErrorTarget {
    PHOTOS,
    LOCATION,
    TITLE,
    START_DATE,
    END_DATE,
    CONTENT,
    TAGS,
    GENERAL,
}
