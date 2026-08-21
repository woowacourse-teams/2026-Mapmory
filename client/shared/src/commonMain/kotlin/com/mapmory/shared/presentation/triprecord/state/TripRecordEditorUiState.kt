package com.mapmory.shared.presentation.triprecord.state

import com.mapmory.shared.domain.model.Location

data class TripRecordEditorUiState(
    val recordId: Long? = null,
    val selectedLocation: Location? = null,
    val title: String = "",
    val content: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val mediaObjectKeys: List<String> = emptyList(),
    val selectedPhotos: List<TripRecordPhotoUiState> = emptyList(),
    val isDirty: Boolean = false,
    val dirtyFields: Set<TripRecordEditorErrorTarget> = emptySet(),
    val isSaving: Boolean = false,
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

    fun isFieldDirty(target: TripRecordEditorErrorTarget): Boolean = target in dirtyFields
}

enum class TripRecordEditorErrorTarget {
    PHOTOS,
    LOCATION,
    TITLE,
    START_DATE,
    END_DATE,
    CONTENT,
    GENERAL,
}
