package com.mapmory.shared.presentation.triprecord.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.TagRules
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMediaDraft
import com.mapmory.shared.domain.model.TripRecordPhotoRules
import com.mapmory.shared.domain.model.dateValidationError
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.usecase.CreateTripRecordUseCase
import com.mapmory.shared.domain.usecase.CreateTagUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.domain.usecase.UpdateTripRecordUseCase
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.triprecord.isSelectableTripRecordDestination
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorErrorTarget
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import com.mapmory.shared.presentation.triprecord.state.toTripRecordPhotoUiState

class TripRecordEditorViewModel(
    private val createTripRecord: CreateTripRecordUseCase,
    private val updateTripRecord: UpdateTripRecordUseCase,
    private val getTripRecord: GetTripRecordUseCase? = null,
    private val regionCatalog: RegionCatalog? = null,
    private val onTripRecordsChanged: () -> Unit = {},
    private val getTags: GetTagsUseCase? = null,
    private val createTag: CreateTagUseCase? = null,
) : ViewModel() {
    private var isRouteInitialized = false

    var uiState by mutableStateOf(TripRecordEditorUiState())
        private set

    var savedRecordId: Long? = null
        private set

    fun reset() {
        uiState = TripRecordEditorUiState()
        savedRecordId = null
        isRouteInitialized = false
    }

    suspend fun initialize(
        recordId: Long?,
        selectedLocation: Location?,
    ) {
        if (isRouteInitialized) return
        isRouteInitialized = true
        loadTags()
        if (recordId == null) {
            startCreating(selectedLocation)
        } else {
            load(recordId)
        }
    }

    fun startCreating(location: Location?) {
        uiState = TripRecordEditorUiState(
            selectedLocation = location?.takeIf(Location::isSelectableTripRecordDestination),
            availableTags = uiState.availableTags,
            tagErrorMessage = uiState.tagErrorMessage,
        )
        savedRecordId = null
    }

    suspend fun load(recordId: Long): Boolean {
        val getRecord = getTripRecord ?: return false
        return getRecord(recordId).fold(
            onSuccess = { record ->
                val location = regionCatalog?.findById(record.locationId) ?: return@fold false
                startEditing(record, location)
                true
            },
            onFailure = { error ->
                uiState = uiState.copy(
                    generalErrorMessage = error.message ?: "여행 기록을 불러오지 못했습니다.",
                )
                false
            },
        )
    }

    fun startEditing(record: TripRecordData, location: Location) {
        val allTags = (uiState.availableTags + record.tags).distinctBy { it.id }
        uiState = TripRecordEditorUiState(
            recordId = record.id,
            selectedLocation = location,
            title = record.title,
            content = record.content,
            startDate = record.startDate,
            endDate = record.endDate.orEmpty(),
            mediaObjectKeys = record.media.map { it.objectKey },
            selectedPhotos = record.media.map { media ->
                SelectedPhoto(
                    id = media.objectKey,
                    displayName = media.objectKey.substringAfterLast('/'),
                    previewBytes = media.previewBytes,
                    originalBytes = media.originalBytes,
                    latitude = media.latitude,
                    longitude = media.longitude,
                    capturedAt = media.capturedAt,
                ).toTripRecordPhotoUiState(media.sortOrder)
                    .copy(isUploaded = true)
            },
            availableTags = allTags,
            selectedTagIds = record.tags.mapTo(linkedSetOf()) { it.id },
        )
    }

    private suspend fun loadTags() {
        val loadTags = getTags ?: return
        uiState = uiState.copy(isTagsLoading = true, tagErrorMessage = null)
        loadTags().fold(
            onSuccess = { tags ->
                uiState = uiState.copy(
                    availableTags = tags,
                    isTagsLoading = false,
                )
            },
            onFailure = { error ->
                uiState = uiState.copy(
                    isTagsLoading = false,
                    tagErrorMessage = error.message ?: "태그를 불러오지 못했습니다.",
                )
            },
        )
    }

    fun updateTagInput(value: String) {
        uiState = uiState.copy(
            tagInput = value,
            tagErrorMessage = null,
            fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
            isDirty = true,
        )
    }

    fun toggleTag(tagId: Long) {
        if (uiState.availableTags.none { it.id == tagId }) return
        val selected = uiState.selectedTagIds
        uiState = when {
            tagId in selected -> uiState.copy(
                selectedTagIds = selected - tagId,
                tagErrorMessage = null,
                fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
                isDirty = true,
            )
            else -> runCatching {
                TagRules.requireCanAddToRecord(selected)
                selected + tagId
            }.fold(
                onSuccess = { updatedSelection ->
                    uiState.copy(
                        selectedTagIds = updatedSelection,
                        tagErrorMessage = null,
                        fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
                        isDirty = true,
                    )
                },
                onFailure = { error -> uiState.copy(tagErrorMessage = error.message) },
            )
        }
    }

    suspend fun createAndSelectTag() {
        val create = createTag ?: return
        val normalizedName = runCatching {
            TagRules.requireCanAddToRecord(uiState.selectedTagIds)
            TagRules.normalizeAndValidateName(uiState.tagInput)
        }.getOrElse { error ->
            uiState = uiState.copy(tagErrorMessage = error.message)
            return
        }

        uiState.availableTags.firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }?.let { tag ->
            uiState = uiState.copy(
                selectedTagIds = uiState.selectedTagIds + tag.id,
                tagInput = "",
                tagErrorMessage = null,
                fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
                isDirty = true,
            )
            return
        }

        uiState = uiState.copy(isCreatingTag = true, tagErrorMessage = null)
        create(normalizedName, uiState.availableTags).fold(
            onSuccess = { tag ->
                uiState = uiState.copy(
                    availableTags = uiState.availableTags + tag,
                    selectedTagIds = uiState.selectedTagIds + tag.id,
                    tagInput = "",
                    isCreatingTag = false,
                    fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
                    isDirty = true,
                )
            },
            onFailure = { error ->
                uiState = uiState.copy(
                    isCreatingTag = false,
                    tagErrorMessage = error.message ?: "태그를 만들지 못했습니다.",
                )
            },
        )
    }

    fun selectLocation(location: Location) {
        if (!location.isSelectableTripRecordDestination()) return
        uiState = uiState.copy(
            selectedLocation = location,
        ).revalidatedAfterChange(TripRecordEditorErrorTarget.LOCATION)
    }

    fun touchLocation() {
        uiState = uiState.revalidatedAfterChange(TripRecordEditorErrorTarget.LOCATION)
    }

    fun clearLocation() {
        uiState = uiState.copy(
            selectedLocation = null,
        ).revalidatedAfterChange(TripRecordEditorErrorTarget.LOCATION)
    }

    fun updateTitle(title: String) {
        uiState = uiState.copy(
            title = title,
        ).revalidatedAfterChange(TripRecordEditorErrorTarget.TITLE)
    }

    fun updateContent(content: String) {
        uiState = uiState.copy(
            content = content,
        ).revalidatedAfterChange(TripRecordEditorErrorTarget.CONTENT)
    }

    fun updateStartDate(startDate: String) {
        uiState = uiState.copy(
            startDate = startDate,
        ).revalidatedAfterChange(TripRecordEditorErrorTarget.START_DATE)
    }

    fun updateEndDate(endDate: String) {
        uiState = uiState.copy(
            endDate = endDate,
        ).revalidatedAfterChange(TripRecordEditorErrorTarget.END_DATE)
    }

    fun addMediaObjectKey(objectKey: String) {
        val trimmedObjectKey = objectKey.trim()
        if (trimmedObjectKey.isBlank() || trimmedObjectKey in uiState.mediaObjectKeys) return
        if (uiState.mediaObjectKeys.size >= TripRecordPhotoRules.MaxPhotosPerRecord) {
            uiState = uiState.withPhotoLimitError()
            return
        }

        uiState = uiState.copy(
            mediaObjectKeys = uiState.mediaObjectKeys + trimmedObjectKey,
            fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.PHOTOS,
        ).revalidatedAfterChange()
    }

    fun addPhotos(photos: List<SelectedPhoto>) {
        val existingIds = uiState.selectedPhotos.mapTo(mutableSetOf()) { photo -> photo.id }
        val newPhotos = photos
            .filterNot { photo -> photo.id in existingIds }
            .distinctBy(SelectedPhoto::id)
        val acceptedPhotos = newPhotos.take(
            TripRecordPhotoRules.remainingSlots(uiState.selectedPhotos.size),
        )
        val merged = buildList {
            addAll(uiState.selectedPhotos)
            acceptedPhotos.forEach { photo ->
                add(photo.toTripRecordPhotoUiState(sortOrder = size))
            }
        }
        val photoErrors = when {
            acceptedPhotos.size < newPhotos.size -> uiState.fieldErrors + (
                TripRecordEditorErrorTarget.PHOTOS to TripRecordPhotoRules.LimitMessage
            )
            newPhotos.isNotEmpty() -> uiState.fieldErrors - TripRecordEditorErrorTarget.PHOTOS
            else -> uiState.fieldErrors
        }
        uiState = uiState.copy(
            selectedPhotos = merged,
            mediaObjectKeys = merged.map { it.id },
            fieldErrors = photoErrors,
        ).revalidatedAfterChange()
    }

    fun setPhotoLoading(isLoading: Boolean) {
        uiState = uiState.copy(isPhotoLoading = isLoading)
    }

    fun removeMediaObjectKey(objectKey: String) {
        uiState = uiState.copy(
            mediaObjectKeys = uiState.mediaObjectKeys - objectKey,
            selectedPhotos = uiState.selectedPhotos.filterNot { it.id == objectKey },
            fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.PHOTOS,
        ).revalidatedAfterChange()
    }

    suspend fun save(): Boolean {
        val state = uiState
        if (state.isPhotoLoading || state.isSaving) return false
        val validationErrors = state.validationErrors()
        if (validationErrors.isNotEmpty()) return fail(validationErrors)

        val location = requireNotNull(state.selectedLocation)

        val draft = TripRecordDraft(
            locationId = location.id,
            title = state.title.trim(),
            content = state.content.trim(),
            startDate = state.startDate.ifBlank { null },
            endDate = state.endDate.ifBlank { null },
            mediaObjectKeys = state.mediaObjectKeys,
            uploadedMediaObjectKeys = state.selectedPhotos
                .filter { photo -> photo.isUploaded }
                .mapTo(mutableSetOf()) { photo -> photo.id },
            localMedia = state.selectedPhotos.mapIndexed { index, photo ->
                TripRecordMediaDraft(
                    objectKey = photo.id,
                    sortOrder = index,
                    previewBytes = photo.previewBytes?.bytesForDecoding(),
                    originalBytes = photo.originalBytes?.bytesForDecoding(),
                    fileName = photo.displayName,
                    latitude = photo.latitude,
                    longitude = photo.longitude,
                    capturedAt = photo.capturedAt,
                )
            },
            tagIds = state.availableTags
                .filter { it.id in state.selectedTagIds }
                .map { it.id },
        )
        uiState = state.copy(isSaving = true, fieldErrors = emptyMap(), generalErrorMessage = null)
        val result = state.recordId?.let { updateTripRecord(it, draft) }
            ?: createTripRecord(draft)

        return result.fold(
            onSuccess = { record ->
                savedRecordId = record.id
                onTripRecordsChanged()
                uiState = uiState.copy(isSaving = false)
                true
            },
            onFailure = { error ->
                val fieldErrors = error.toEditorFieldErrors()
                uiState = uiState.copy(
                    isSaving = false,
                    isDirty = true,
                    dirtyFields = uiState.dirtyFields + fieldErrors.keys,
                    fieldErrors = fieldErrors,
                    generalErrorMessage = if (fieldErrors.isEmpty()) {
                        error.message ?: "여행 기록을 저장하지 못했습니다."
                    } else {
                        null
                    },
                )
                false
            },
        )
    }

    private fun fail(errors: Map<TripRecordEditorErrorTarget, String>): Boolean {
        uiState = uiState.copy(
            isDirty = true,
            dirtyFields = uiState.dirtyFields + errors.keys,
            fieldErrors = errors,
            generalErrorMessage = null,
        )
        return false
    }
}

private fun TripRecordEditorUiState.revalidatedAfterChange(
    dirtyTarget: TripRecordEditorErrorTarget? = null,
): TripRecordEditorUiState {
    if (dirtyTarget == null) {
        return copy(isDirty = true, generalErrorMessage = null)
    }

    val updatedDirtyFields = if (dirtyTarget in dirtyFields) dirtyFields else dirtyFields + dirtyTarget
    val retainedErrors = fieldErrors.filterKeys { target ->
        target != dirtyTarget &&
            !(dirtyTarget.isDateTarget() && target.isDateTarget())
    }
    val dateRangeErrorTarget = when (dirtyTarget) {
        TripRecordEditorErrorTarget.START_DATE,
        TripRecordEditorErrorTarget.END_DATE -> dirtyTarget

        else -> fieldErrors.keys.firstOrNull { target ->
            target == TripRecordEditorErrorTarget.START_DATE ||
                target == TripRecordEditorErrorTarget.END_DATE
        } ?: TripRecordEditorErrorTarget.END_DATE
    }
    return copy(
        isDirty = true,
        dirtyFields = updatedDirtyFields,
        fieldErrors = retainedErrors + validationErrors(dateRangeErrorTarget)
            .filterKeys(updatedDirtyFields::contains),
        generalErrorMessage = null,
    )
}

private fun TripRecordEditorUiState.withPhotoLimitError(): TripRecordEditorUiState = copy(
    isDirty = true,
    dirtyFields = dirtyFields + TripRecordEditorErrorTarget.PHOTOS,
    fieldErrors = fieldErrors + (
        TripRecordEditorErrorTarget.PHOTOS to TripRecordPhotoRules.LimitMessage
    ),
    generalErrorMessage = null,
)

private fun TripRecordEditorErrorTarget.isDateTarget(): Boolean =
    this == TripRecordEditorErrorTarget.START_DATE || this == TripRecordEditorErrorTarget.END_DATE

private fun TripRecordEditorUiState.validationErrors(
    dateRangeErrorTarget: TripRecordEditorErrorTarget = TripRecordEditorErrorTarget.END_DATE,
): Map<TripRecordEditorErrorTarget, String> = buildMap {
    if (
        mediaObjectKeys.size > TripRecordPhotoRules.MaxPhotosPerRecord ||
        selectedPhotos.size > TripRecordPhotoRules.MaxPhotosPerRecord
    ) {
        put(TripRecordEditorErrorTarget.PHOTOS, TripRecordPhotoRules.LimitMessage)
    }
    if (selectedLocation == null) {
        put(TripRecordEditorErrorTarget.LOCATION, "장소를 선택해 주세요.")
    } else if (!selectedLocation.isSelectableTripRecordDestination()) {
        put(TripRecordEditorErrorTarget.LOCATION, "장소를 선택해 주세요.")
    }
    if (title.isBlank()) {
        put(TripRecordEditorErrorTarget.TITLE, "제목을 입력해 주세요.")
    } else if (title.length > MaxTitleLength) {
        put(TripRecordEditorErrorTarget.TITLE, "제목은 200자 이하여야 합니다.")
    }

    val dateError = TripRecordDraft(
        locationId = selectedLocation?.id ?: 0L,
        title = title,
        content = content,
        startDate = startDate.ifBlank { null },
        endDate = endDate.ifBlank { null },
        mediaObjectKeys = mediaObjectKeys,
    ).dateValidationError()
    if (dateError != null) {
        val target = when (dateError) {
            "시작일을 입력해 주세요." -> TripRecordEditorErrorTarget.START_DATE
            "올바른 시작일을 입력해 주세요." -> TripRecordEditorErrorTarget.START_DATE
            "올바른 종료일을 입력해 주세요." -> TripRecordEditorErrorTarget.END_DATE
            else -> dateRangeErrorTarget
        }
        put(target, dateError)
    }
}

private const val MaxTitleLength = 200

internal fun Throwable.toEditorFieldErrors(): Map<TripRecordEditorErrorTarget, String> {
    val apiError = this as? MapmoryApiException
    val errorsByTarget = apiError?.errors.orEmpty()
        .mapNotNull { error ->
            error.field.toEditorErrorTarget()?.let { target -> target to error.detail }
        }
        .toMap()
    if (errorsByTarget.isNotEmpty()) return errorsByTarget

    val target = when (apiError?.code) {
        "INVALID_FILE_TYPE",
        "FILE_SIZE_EXCEEDED",
        "TOO_MANY_FILES",
        "MEDIA_NOT_UPLOADED",
        "STORAGE_UNAVAILABLE",
        "INVALID_OBJECT_KEY" -> TripRecordEditorErrorTarget.PHOTOS

        "INVALID_REGION_CODE",
        "INVALID_REGION_TYPE",
        "REGION_REQUIRED" -> TripRecordEditorErrorTarget.LOCATION

        "INVALID_TRAVEL_DATE_RANGE" -> TripRecordEditorErrorTarget.END_DATE
        "TOO_MANY_TAGS", "INVALID_TAG_IDS" -> TripRecordEditorErrorTarget.TAGS
        else -> when {
            apiError?.instance.orEmpty().contains("/uploads/") -> TripRecordEditorErrorTarget.PHOTOS
            message.orEmpty().contains("사진") -> TripRecordEditorErrorTarget.PHOTOS
            else -> null
        }
    } ?: return emptyMap()

    return mapOf(target to (message ?: "입력한 내용을 확인해 주세요."))
}

private fun String.toEditorErrorTarget(): TripRecordEditorErrorTarget? = when {
    this in setOf("countryCode", "provinceCode", "districtCode") -> TripRecordEditorErrorTarget.LOCATION
    this == "title" -> TripRecordEditorErrorTarget.TITLE
    this == "startDate" -> TripRecordEditorErrorTarget.START_DATE
    this == "endDate" -> TripRecordEditorErrorTarget.END_DATE
    this == "content" -> TripRecordEditorErrorTarget.CONTENT
    startsWith("objectKeys") || startsWith("files") -> TripRecordEditorErrorTarget.PHOTOS
    startsWith("tagIds") -> TripRecordEditorErrorTarget.TAGS
    else -> null
}
