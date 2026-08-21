package com.mapmory.shared.presentation.triprecord.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mapmory.shared.domain.TripRecords
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorErrorTarget
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordEffect
import com.mapmory.shared.presentation.triprecord.state.TripRecordFilterUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordsUiState
import com.mapmory.shared.presentation.triprecord.state.toTripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.toTripRecordPhotoUiState
import kotlinx.datetime.LocalDate

sealed interface TripRecordAction {
    data class KeywordChanged(val keyword: String) : TripRecordAction

    data class LocationFilterChanged(val locationId: Long?) : TripRecordAction

    data class MapLocationSelected(val location: Location) : TripRecordAction

    data class RecordSelected(val recordId: Long) : TripRecordAction

    data class StartCreating(val selectedLocation: Location? = null) : TripRecordAction

    data class StartEditing(val recordId: Long) : TripRecordAction

    data class LocationSelected(val location: Location) : TripRecordAction

    data object LocationTouched : TripRecordAction

    data class TitleChanged(val title: String) : TripRecordAction

    data class ContentChanged(val content: String) : TripRecordAction

    data class StartDateChanged(val date: String) : TripRecordAction

    data class EndDateChanged(val date: String) : TripRecordAction

    data class PhotosAdded(val photos: List<SelectedPhoto>) : TripRecordAction

    data class PhotoRemoved(val photoId: String) : TripRecordAction

    data object Save : TripRecordAction

    data class Delete(val recordId: Long) : TripRecordAction

    data object EffectHandled : TripRecordAction
}

/**
 * 여행 기록 도메인 컬렉션을 단일 원본으로 관리한다.
 * UI는 [uiState]만 읽고 모든 변경은 [onAction]으로 전달한다.
 */
class TripRecordsViewModel(
    locations: List<Location>,
    initialRecords: TripRecords = TripRecords(),
) {
    private val locations = locations.toList()
    private val locationsById = this.locations.associateBy(Location::id)
    private var domainRecords = initialRecords
    private var photosByRecordId: Map<Long, List<TripRecordPhotoUiState>> = initialRecords.tripRecords
        .mapNotNull { record ->
            record.imageUrl.takeIf(String::isNotBlank)?.let { imageUrl ->
                record.id to listOf(
                    TripRecordPhotoUiState(
                        id = imageUrl,
                        displayName = imageUrl.substringAfterLast('/'),
                        previewBytes = null,
                        sortOrder = 0,
                    ),
                )
            }
        }
        .toMap()

    var uiState by mutableStateOf(TripRecordsUiState())
        private set

    init {
        publishRecords()
    }

    fun onAction(action: TripRecordAction) {
        when (action) {
            is TripRecordAction.KeywordChanged -> updateFilter(
                uiState.filter.copy(keyword = action.keyword),
            )

            is TripRecordAction.LocationFilterChanged -> updateFilter(
                uiState.filter.copy(locationId = action.locationId),
            )

            is TripRecordAction.MapLocationSelected -> selectMapLocation(action.location)
            is TripRecordAction.RecordSelected -> emit(TripRecordEffect.OpenDetail(action.recordId))
            is TripRecordAction.StartCreating -> startCreating(action.selectedLocation)
            is TripRecordAction.StartEditing -> startEditing(action.recordId)
            is TripRecordAction.LocationSelected -> updateEditor {
                copy(
                    selectedLocation = action.location,
                ).revalidatedAfterChange(TripRecordEditorErrorTarget.LOCATION)
            }

            TripRecordAction.LocationTouched -> updateEditor {
                revalidatedAfterChange(TripRecordEditorErrorTarget.LOCATION)
            }

            is TripRecordAction.TitleChanged -> updateEditor {
                copy(
                    title = action.title,
                ).revalidatedAfterChange(TripRecordEditorErrorTarget.TITLE)
            }

            is TripRecordAction.ContentChanged -> updateEditor {
                copy(
                    content = action.content,
                ).revalidatedAfterChange()
            }

            is TripRecordAction.StartDateChanged -> updateEditor {
                copy(
                    startDate = action.date,
                ).revalidatedAfterChange(TripRecordEditorErrorTarget.START_DATE)
            }

            is TripRecordAction.EndDateChanged -> updateEditor {
                copy(
                    endDate = action.date,
                ).revalidatedAfterChange(TripRecordEditorErrorTarget.END_DATE)
            }

            is TripRecordAction.PhotosAdded -> addPhotos(action.photos)
            is TripRecordAction.PhotoRemoved -> removePhoto(action.photoId)
            TripRecordAction.Save -> save()
            is TripRecordAction.Delete -> delete(action.recordId)
            TripRecordAction.EffectHandled -> uiState = uiState.copy(effect = null)
        }
    }

    private fun selectMapLocation(location: Location) {
        if (domainRecords.tripRecords.any { record -> locationContains(location, record.location) }) {
            updateFilter(TripRecordFilterUiState(locationId = location.id))
            emit(TripRecordEffect.OpenRecords)
        } else {
            startCreating(location)
        }
    }

    private fun startCreating(selectedLocation: Location?) {
        uiState = uiState.copy(
            editor = TripRecordEditorUiState(selectedLocation = selectedLocation),
            effect = TripRecordEffect.OpenEditor,
        )
    }

    private fun startEditing(recordId: Long) {
        val record = domainRecords.tripRecords.firstOrNull { it.id == recordId } ?: return
        val photos = photosByRecordId[record.id].orEmpty()
        uiState = uiState.copy(
            editor = TripRecordEditorUiState(
                recordId = record.id,
                selectedLocation = locations.firstOrNull { it.name == record.location },
                title = record.tripRecordTitle,
                content = record.tripRecordDescription.orEmpty(),
                startDate = record.startTripDate?.toString().orEmpty(),
                endDate = record.endTripDate?.toString().orEmpty(),
                mediaObjectKeys = photos.map(TripRecordPhotoUiState::id),
                selectedPhotos = photos,
            ),
            effect = TripRecordEffect.OpenEditor,
        )
    }

    private fun addPhotos(incoming: List<SelectedPhoto>) {
        val editor = uiState.editor
        val merged = buildList {
            addAll(editor.selectedPhotos)
            incoming.forEach { photo ->
                if (none { it.id == photo.id }) {
                    add(photo.toTripRecordPhotoUiState(sortOrder = size))
                }
            }
        }

        val updatedEditor = editor.copy(
            selectedPhotos = merged,
            mediaObjectKeys = merged.map(TripRecordPhotoUiState::id),
            fieldErrors = editor.fieldErrors - TripRecordEditorErrorTarget.PHOTOS,
        ).revalidatedAfterChange()
        uiState = uiState.copy(
            editor = updatedEditor,
        )
    }

    private fun removePhoto(photoId: String) {
        val remaining = uiState.editor.selectedPhotos
            .filterNot { it.id == photoId }
            .mapIndexed { index, photo -> photo.copy(sortOrder = index) }
        updateEditor {
            copy(
                selectedPhotos = remaining,
                mediaObjectKeys = remaining.map(TripRecordPhotoUiState::id),
                fieldErrors = fieldErrors - TripRecordEditorErrorTarget.PHOTOS,
            ).revalidatedAfterChange()
        }
    }

    private fun save() {
        val editor = uiState.editor
        val title = editor.title.trim()
        val startDate = editor.startDate.takeIf(String::isNotBlank)?.toLocalDateOrNull()
        val endDate = editor.endDate.takeIf(String::isNotBlank)?.toLocalDateOrNull()
        val validationErrors = editor.validationErrors(startDate, endDate)
        if (validationErrors.isNotEmpty()) return fail(validationErrors)

        val location = requireNotNull(editor.selectedLocation)

        uiState = uiState.copy(
            editor = editor.copy(isSaving = true, fieldErrors = emptyMap(), generalErrorMessage = null),
        )
        val photos = editor.selectedPhotos.mapIndexed { index, photo -> photo.copy(sortOrder = index) }
        val imageUrl = photos.firstOrNull()?.id.orEmpty()

        val savedRecord = runCatching {
            editor.recordId?.let { recordId ->
                val editingRecord = domainRecords.tripRecords.firstOrNull { it.id == recordId }
                    ?: error("수정할 여행 기록을 찾을 수 없습니다.")
                domainRecords = domainRecords.editTripRecord(
                    editingRecord = editingRecord,
                    editingImage = imageUrl,
                    editingTitle = title,
                    editingDescription = editor.content.trim(),
                    editingStartTripDate = startDate,
                    editingEndTripDate = endDate,
                    clearStartTripDate = editor.startDate.isBlank(),
                    clearEndTripDate = editor.endDate.isBlank(),
                    editingLocation = location.name,
                )
                domainRecords.tripRecords.first { it.id == recordId }
            } ?: run {
                domainRecords = domainRecords.addTripRecord(
                    imageUri = imageUrl,
                    tripRecordTitle = title,
                    tripRecordDescription = editor.content.trim().ifBlank { null },
                    tripLocation = location.name,
                    startTripDate = startDate,
                    endTripDate = endDate,
                )
                domainRecords.tripRecords.last()
            }
        }.getOrElse { error ->
            fail(
                generalErrorMessage = error.message ?: "여행 기록을 저장하지 못했습니다.",
            )
            return
        }

        photosByRecordId = photosByRecordId + (savedRecord.id to photos)
        val wasEditing = editor.recordId != null
        publishRecords(
            editor = editor.copy(isSaving = false, fieldErrors = emptyMap(), generalErrorMessage = null),
            filter = TripRecordFilterUiState(),
            effect = if (wasEditing) {
                TripRecordEffect.OpenDetail(savedRecord.id, replaceCurrent = true)
            } else {
                TripRecordEffect.OpenRecords
            },
        )
    }

    private fun delete(recordId: Long) {
        val record = domainRecords.tripRecords.firstOrNull { it.id == recordId } ?: return
        domainRecords = domainRecords.removeTripRecord(record)
        photosByRecordId = photosByRecordId - recordId
        publishRecords(effect = TripRecordEffect.CloseDetail)
    }

    private fun updateFilter(filter: TripRecordFilterUiState) {
        publishRecords(filter = filter)
    }

    private fun updateEditor(transform: TripRecordEditorUiState.() -> TripRecordEditorUiState) {
        uiState = uiState.copy(editor = uiState.editor.transform())
    }

    private fun fail(errors: Map<TripRecordEditorErrorTarget, String>) {
        updateEditor {
            copy(
                isDirty = true,
                dirtyFields = dirtyFields + errors.keys,
                isSaving = false,
                fieldErrors = errors,
                generalErrorMessage = null,
            )
        }
    }

    private fun fail(generalErrorMessage: String) {
        updateEditor { copy(isSaving = false, fieldErrors = emptyMap(), generalErrorMessage = generalErrorMessage) }
    }

    private fun emit(effect: TripRecordEffect) {
        uiState = uiState.copy(effect = effect)
    }

    private fun publishRecords(
        editor: TripRecordEditorUiState = uiState.editor,
        filter: TripRecordFilterUiState = uiState.filter,
        effect: TripRecordEffect? = uiState.effect,
    ) {
        val records = domainRecords.tripRecords.map { record ->
            record.toTripRecordItemUiState(photosByRecordId[record.id].orEmpty())
        }
        uiState = TripRecordsUiState(
            records = records,
            visibleRecords = records.filter { record -> record.matches(filter) },
            filter = filter,
            editor = editor,
            effect = effect,
        )
    }

    private fun TripRecordItemUiState.matches(
        filter: TripRecordFilterUiState,
    ): Boolean {
        val selectedLocation = filter.locationId?.let(locationsById::get)
        val matchesLocation = selectedLocation == null || locationContains(selectedLocation, locationName)
        val matchesKeyword = filter.keyword.isBlank() ||
            title.contains(filter.keyword, ignoreCase = true) ||
            content.contains(filter.keyword, ignoreCase = true)
        return matchesLocation && matchesKeyword
    }

    private fun locationContains(selected: Location, recordLocationName: String): Boolean {
        val recordLocation = locations.firstOrNull { it.name == recordLocationName } ?: return false
        return when {
            selected.regionCode == "KR" ->
                recordLocation.countryId == KoreaCountryId || recordLocation.regionCode == "KR"

            selected.countryId == KoreaCountryId && selected.type == LocationType.PROVINCE ->
                recordLocation.id == selected.id || recordLocation.parentId == selected.id

            else -> recordLocation.id == selected.id
        }
    }
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching {
    LocalDate.parse(trim().replace(" ", "").replace('.', '-'))
}.getOrNull()

private fun TripRecordEditorUiState.revalidatedAfterChange(
    dirtyTarget: TripRecordEditorErrorTarget? = null,
): TripRecordEditorUiState {
    if (dirtyTarget == null) {
        return copy(isDirty = true, generalErrorMessage = null)
    }

    val startDateValue = startDate.takeIf(String::isNotBlank)?.toLocalDateOrNull()
    val endDateValue = endDate.takeIf(String::isNotBlank)?.toLocalDateOrNull()
    val updatedDirtyFields = if (dirtyTarget in dirtyFields) dirtyFields else dirtyFields + dirtyTarget
    val nonValidationErrors = fieldErrors.filterKeys { target ->
        target == TripRecordEditorErrorTarget.PHOTOS
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
        fieldErrors = nonValidationErrors + validationErrors(
            startDateValue = startDateValue,
            endDateValue = endDateValue,
            dateRangeErrorTarget = dateRangeErrorTarget,
        ).filterKeys(updatedDirtyFields::contains),
        generalErrorMessage = null,
    )
}

private fun TripRecordEditorUiState.validationErrors(
    startDateValue: LocalDate?,
    endDateValue: LocalDate?,
    dateRangeErrorTarget: TripRecordEditorErrorTarget = TripRecordEditorErrorTarget.END_DATE,
): Map<TripRecordEditorErrorTarget, String> = buildMap {
    if (selectedLocation == null) {
        put(TripRecordEditorErrorTarget.LOCATION, "장소를 선택해 주세요.")
    }
    if (title.isBlank()) {
        put(TripRecordEditorErrorTarget.TITLE, "제목을 입력해 주세요.")
    }
    if (startDate.isNotBlank() && startDateValue == null) {
        put(TripRecordEditorErrorTarget.START_DATE, "올바른 시작일을 입력해 주세요.")
    }
    if (endDate.isNotBlank() && endDateValue == null) {
        put(TripRecordEditorErrorTarget.END_DATE, "올바른 종료일을 입력해 주세요.")
    }
    if (startDateValue != null && endDateValue != null && startDateValue > endDateValue) {
        put(dateRangeErrorTarget, "종료일은 시작일보다 빠를 수 없습니다.")
    }
}

private const val KoreaCountryId = 1L
