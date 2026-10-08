package com.mapmory.shared.presentation.triprecord.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mapmory.shared.app.BackgroundTripRecordSaver
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceReference
import com.mapmory.shared.domain.model.PlaceRules
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.model.TagRules
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMediaDraft
import com.mapmory.shared.domain.model.TripRecordPhotoRules
import com.mapmory.shared.domain.model.dateValidationError
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.repository.PlaceRepository
import com.mapmory.shared.domain.usecase.CreateTripRecordUseCase
import com.mapmory.shared.domain.usecase.CreateTagUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.domain.usecase.UpdateTripRecordUseCase
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.triprecord.isSelectableTripRecordDestination
import com.mapmory.shared.presentation.triprecord.tripRecordCountryCode
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorErrorTarget
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import com.mapmory.shared.presentation.triprecord.state.toTripRecordPhotoUiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class TripRecordEditorViewModel(
    private val createTripRecord: CreateTripRecordUseCase,
    private val updateTripRecord: UpdateTripRecordUseCase,
    private val getTripRecord: GetTripRecordUseCase? = null,
    private val regionCatalog: RegionCatalog? = null,
    private val onTripRecordsChanged: () -> Unit = {},
    private val getTags: GetTagsUseCase? = null,
    private val createTag: CreateTagUseCase? = null,
    private val backgroundTripRecordSaver: BackgroundTripRecordSaver? = null,
    private val recordedPhotoIndex: com.mapmory.shared.data.media.RecordedPhotoIndex? = null,
    private val placeRepository: PlaceRepository? = null,
    private val newPlaceSessionToken: () -> String = ::randomPlaceSessionToken,
) : ViewModel() {
    val recordedPhotoIds = recordedPhotoIndex?.ids ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>())
    val pendingPhotoIds = backgroundTripRecordSaver?.pendingPhotoIds ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>())
    private var isRouteInitialized = false
    private var placeSearchGeneration = 0
    private var placeSelectionGeneration = 0
    private var placeSessionToken: String? = null
    private var rejectedPlaceName: String? = null

    var uiState by mutableStateOf(TripRecordEditorUiState())
        private set

    var savedRecordId: Long? = null
        private set

    fun reset() {
        placeSearchGeneration += 1
        placeSelectionGeneration += 1
        placeSessionToken = null
        rejectedPlaceName = null
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
        recordedPhotoIndex?.initialize()
        loadTags()
        if (recordId == null) {
            startCreating(selectedLocation)
        } else {
            load(recordId)
        }
    }

    fun startCreating(location: Location?) {
        placeSearchGeneration += 1
        placeSelectionGeneration += 1
        placeSessionToken = null
        rejectedPlaceName = null
        uiState = TripRecordEditorUiState(
            selectedLocation = location?.takeIf(Location::isSelectableTripRecordDestination),
            isPlaceSearchAvailable = placeRepository != null,
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
        placeSearchGeneration += 1
        placeSelectionGeneration += 1
        placeSessionToken = null
        rejectedPlaceName = null
        val allTags = (uiState.availableTags + record.tags).distinctBy { it.id }
        uiState = TripRecordEditorUiState(
            recordId = record.id,
            selectedLocation = location,
            selectedPlace = record.place,
            isPlaceSearchAvailable = placeRepository != null,
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
                    .copy(
                        isUploaded = true,
                        localPhotoId = media.localPreviewKey,
                        previewUri = media.previewUri,
                        fullResolutionUri = media.url,
                    )
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
                TagRules.validateRecordTagIds(selected)
                require(uiState.selectedTagCount < TagRules.MaxTagsPerRecord) {
                    TagRules.RecordLimitMessage
                }
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

    fun togglePendingTag(name: String) {
        if (name !in uiState.pendingTagNames) return
        val selected = uiState.selectedPendingTagNames
        uiState = when {
            name in selected -> uiState.copy(
                selectedPendingTagNames = selected - name,
                tagErrorMessage = null,
                fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
                isDirty = true,
            )
            else -> runCatching {
                require(uiState.selectedTagCount < TagRules.MaxTagsPerRecord) {
                    TagRules.RecordLimitMessage
                }
                selected + name
            }.fold(
                onSuccess = { updatedSelection ->
                    uiState.copy(
                        selectedPendingTagNames = updatedSelection,
                        tagErrorMessage = null,
                        fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
                        isDirty = true,
                    )
                },
                onFailure = { error -> uiState.copy(tagErrorMessage = error.message) },
            )
        }
    }

    fun createAndSelectTag() {
        val normalizedName = runCatching {
            require(uiState.selectedTagCount < TagRules.MaxTagsPerRecord) {
                TagRules.RecordLimitMessage
            }
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

        uiState.pendingTagNames.firstOrNull { it.equals(normalizedName, ignoreCase = true) }?.let { name ->
            uiState = uiState.copy(
                selectedPendingTagNames = uiState.selectedPendingTagNames + name,
                tagInput = "",
                tagErrorMessage = null,
                fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
                isDirty = true,
            )
            return
        }

        runCatching {
            require(uiState.availableTags.size + uiState.pendingTagNames.size < TagRules.MaxTagsPerMember) {
                TagRules.MemberLimitMessage
            }
            require(
                uiState.availableTags.none { it.name.equals(normalizedName, ignoreCase = true) },
            ) { TagRules.DuplicateNameMessage }
        }.onFailure { error ->
            uiState = uiState.copy(tagErrorMessage = error.message)
        }.onSuccess {
            uiState = uiState.copy(
                pendingTagNames = uiState.pendingTagNames + normalizedName,
                selectedPendingTagNames = uiState.selectedPendingTagNames + normalizedName,
                tagInput = "",
                tagErrorMessage = null,
                fieldErrors = uiState.fieldErrors - TripRecordEditorErrorTarget.TAGS,
                isDirty = true,
            )
        }
    }

    fun selectLocation(location: Location) {
        if (!location.isSelectableTripRecordDestination()) return
        // 지역을 직접 고르면 장소 검색 세션은 끝난다.
        placeSessionToken = null
        val place = uiState.selectedPlace
        // 다른 나라를 고르면 서버가 PLACE_COUNTRY_MISMATCH로 거절하므로 장소 연결을 뺀다.
        val placeCountryCode = place?.countryCode
        val keepsPlace = placeCountryCode == null || placeCountryCode == location.tripRecordCountryCode()
        uiState = uiState.copy(
            selectedLocation = location,
            selectedPlace = place.takeIf { keepsPlace },
            placeSelectionErrorMessage = null,
            manualRegionRequired = false,
        ).revalidatedAfterChange(TripRecordEditorErrorTarget.LOCATION)
    }

    fun clearPlaceSearch() {
        placeSearchGeneration += 1
        placeSelectionGeneration += 1
        uiState = uiState.copy(
            placeSearchResults = emptyList(),
            isSearchingPlaces = false,
            hasSearchedPlaces = false,
            placeSearchErrorMessage = null,
            placeSelectionErrorMessage = null,
            isSelectingPlace = false,
        )
    }

    suspend fun searchPlaces(query: String) {
        val normalizedQuery = query.trim()
        val repository = placeRepository ?: return
        if (!PlaceRules.isSearchableQuery(normalizedQuery)) return
        // 앱에서 고를 수 없는 장소로 거절한 후보를 같은 이름으로 다시 검색하지 않는다.
        if (normalizedQuery == rejectedPlaceName) return
        val generation = placeSearchGeneration
        uiState = uiState.copy(isSearchingPlaces = true, placeSearchErrorMessage = null)
        repository.searchPlaces(normalizedQuery, currentPlaceSessionToken()).fold(
            onSuccess = { candidates ->
                if (generation == placeSearchGeneration) {
                    uiState = uiState.copy(
                        placeSearchResults = candidates.take(PlaceRules.MaxCandidates),
                        isSearchingPlaces = false,
                        hasSearchedPlaces = true,
                    )
                }
            },
            onFailure = { error ->
                if (generation == placeSearchGeneration) {
                    uiState = uiState.copy(
                        placeSearchResults = emptyList(),
                        isSearchingPlaces = false,
                        hasSearchedPlaces = true,
                        placeSearchErrorMessage = when {
                            error is MapmoryApiException && error.code == "PLACE_PROVIDER_UNAVAILABLE" ->
                                "장소 검색을 지금 사용할 수 없어요. 잠시 후 다시 시도해 주세요."
                            else -> error.message ?: "장소를 검색하지 못했습니다."
                        },
                    )
                }
            },
        )
    }

    suspend fun selectPlace(candidate: PlaceCandidate): Location? {
        val repository = placeRepository ?: return null
        val generation = ++placeSelectionGeneration
        uiState = uiState.copy(
            isSelectingPlace = true,
            placeSelectionErrorMessage = null,
            placeSearchResults = emptyList(),
            isSearchingPlaces = false,
            hasSearchedPlaces = false,
        )
        // 선택 조회로 검색 세션이 끝나므로 다음 검색은 새 토큰을 쓴다.
        val sessionToken = currentPlaceSessionToken()
        placeSessionToken = null
        return repository.selectPlace(candidate.placeId, sessionToken).fold(
            onSuccess = { selection ->
                if (generation != placeSelectionGeneration) return@fold null
                if (selection.isOutsideRegionCatalog(regionCatalog)) {
                    rejectedPlaceName = candidate.name.trim()
                    uiState = uiState.copy(
                        selectedPlace = null,
                        isSelectingPlace = false,
                        placeSelectionErrorMessage = PlaceOutsideRegionCatalogMessage,
                        manualRegionRequired = false,
                    ).revalidatedAfterChange()
                    return@fold null
                }
                rejectedPlaceName = null
                // 검색창에 고른 후보의 이름이 들어가므로 같은 이름을 보여 준다.
                // Google 장소는 선택 응답에 이름이 없다.
                val place = selection.place.copy(
                    name = candidate.name,
                    address = candidate.address,
                )
                val location = selection.toSelectableLocation(regionCatalog)
                uiState = uiState.copy(
                    selectedPlace = place,
                    selectedLocation = location,
                    isSelectingPlace = false,
                    placeSelectionErrorMessage = null,
                    manualRegionRequired = selection.manualRegionRequired || location == null,
                ).revalidatedAfterChange(TripRecordEditorErrorTarget.LOCATION)
                location
            },
            onFailure = { error ->
                if (generation == placeSelectionGeneration) {
                    uiState = uiState.copy(
                        isSelectingPlace = false,
                        placeSelectionErrorMessage = error.message ?: "장소 정보를 불러오지 못했습니다.",
                    )
                }
                null
            },
        )
    }

    private fun currentPlaceSessionToken(): String =
        placeSessionToken ?: newPlaceSessionToken().also { placeSessionToken = it }

    fun clearSelectedPlace() {
        placeSelectionGeneration += 1
        uiState = uiState.copy(
            selectedPlace = null,
            isSelectingPlace = false,
            placeSelectionErrorMessage = null,
            manualRegionRequired = false,
        ).revalidatedAfterChange()
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

    /** Creation derives its dates from the chosen photos; editing keeps explicit dates. */
    fun useSelectedPhotoDates(today: String) {
        if (uiState.recordId != null) return
        val dates = uiState.selectedPhotos.mapNotNull { photo ->
            photo.capturedAt?.take(10)?.replace('.', '-')?.takeIf { value ->
                runCatching { kotlinx.datetime.LocalDate.parse(value) }.isSuccess
            }
        }.sorted()
        uiState = uiState.copy(
            startDate = dates.firstOrNull() ?: today,
            endDate = dates.lastOrNull()?.takeIf { it != dates.first() }.orEmpty(),
        )
    }

    suspend fun save(): Boolean {
        val state = uiState
        if (state.isPhotoLoading || state.isSaving) return false
        val validationErrors = state.validationErrors()
        if (validationErrors.isNotEmpty()) return fail(validationErrors)

        uiState = state.copy(isSaving = true, fieldErrors = emptyMap(), generalErrorMessage = null)
        var availableTags = state.availableTags
        var selectedTagIds = state.selectedTagIds
        var pendingTagNames = state.pendingTagNames
        var selectedPendingTagNames = state.selectedPendingTagNames
        val pendingSelectedNames = state.pendingTagNames.filter { it in state.selectedPendingTagNames }
        for (pendingTagName in pendingSelectedNames) {
            val create = createTag
            if (create == null) {
                return failTagSave(IllegalStateException("태그를 저장하지 못했습니다."))
            }
            val result = create(pendingTagName, availableTags)
            if (result.isFailure) {
                return failTagSave(
                    result.exceptionOrNull() ?: IllegalStateException("태그를 저장하지 못했습니다."),
                )
            }
            val tag = result.getOrThrow()
            availableTags += tag
            selectedTagIds += tag.id
            pendingTagNames -= pendingTagName
            selectedPendingTagNames -= pendingTagName
            uiState = uiState.copy(
                availableTags = availableTags,
                selectedTagIds = selectedTagIds,
                pendingTagNames = pendingTagNames,
                selectedPendingTagNames = selectedPendingTagNames,
            )
        }
        uiState = uiState.copy(
            availableTags = availableTags,
            selectedTagIds = selectedTagIds,
            pendingTagNames = pendingTagNames,
            selectedPendingTagNames = selectedPendingTagNames,
        )

        val draft = state.toDraft(
            availableTags = availableTags,
            selectedTagIds = selectedTagIds,
        )
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
                    generalErrorMessage = if (fieldErrors.isNotEmpty()) {
                        null
                    } else {
                        error.message ?: "여행 기록을 저장하지 못했습니다."
                    },
                )
                false
            },
        )
    }

    suspend fun saveInBackground(): Boolean {
        val state = uiState
        if (state.recordId != null) return save()
        if (state.isPhotoLoading || state.isSaving) return false
        val validationErrors = state.validationErrors()
        if (validationErrors.isNotEmpty()) return fail(validationErrors)
        val saver = backgroundTripRecordSaver ?: return save()

        // 현재 생성 화면에는 태그 입력이 없지만, 추후 다시 노출되더라도 생성 전 태그가
        // 유실되지 않도록 이 경우에는 기존 저장 경로를 유지한다.
        if (state.pendingTagNames.any { it in state.selectedPendingTagNames }) return save()

        uiState = state.copy(isSaving = true, fieldErrors = emptyMap(), generalErrorMessage = null)
        saver.enqueue(
            state.toDraft(
                availableTags = state.availableTags,
                selectedTagIds = state.selectedTagIds,
            ),
            locationName = requireNotNull(state.selectedLocation).name,
        )
        uiState = uiState.copy(isSaving = false, isDirty = false)
        return true
    }

    private fun TripRecordEditorUiState.toDraft(
        availableTags: List<Tag>,
        selectedTagIds: Set<Long>,
    ): TripRecordDraft = TripRecordDraft(
        locationId = requireNotNull(selectedLocation).id,
        title = title,
        content = content.trim().takeIf(String::isNotEmpty),
        startDate = startDate,
        endDate = endDate.ifBlank { null },
        place = uiState.selectedPlace,
        mediaObjectKeys = mediaObjectKeys,
        uploadedMediaObjectKeys = selectedPhotos
            .filter { photo -> photo.isUploaded }
            .mapTo(mutableSetOf()) { photo -> photo.id },
        localMedia = selectedPhotos.mapIndexed { index, photo ->
            TripRecordMediaDraft(
                objectKey = photo.id,
                sortOrder = index,
                previewBytes = photo.previewBytes?.bytesForDecoding(),
                localPreviewKey = photo.localPhotoId ?: photo.id,
                originalBytes = photo.originalBytes?.bytesForDecoding(),
                fileName = photo.displayName,
                latitude = photo.latitude,
                longitude = photo.longitude,
                capturedAt = photo.capturedAt,
            )
        },
        tagIds = availableTags
            .filter { it.id in selectedTagIds }
            .map { it.id },
    )

    private fun fail(errors: Map<TripRecordEditorErrorTarget, String>): Boolean {
        uiState = uiState.copy(
            isDirty = true,
            dirtyFields = uiState.dirtyFields + errors.keys,
            fieldErrors = errors,
            generalErrorMessage = null,
        )
        return false
    }

    private fun failTagSave(error: Throwable): Boolean {
        val message = error.toEditorFieldErrors()[TripRecordEditorErrorTarget.TAGS]
            ?: error.message
            ?: "태그를 저장하지 못했습니다."
        uiState = uiState.copy(
            isSaving = false,
            isDirty = true,
            dirtyFields = uiState.dirtyFields + TripRecordEditorErrorTarget.TAGS,
            fieldErrors = uiState.fieldErrors + (TripRecordEditorErrorTarget.TAGS to message),
            tagErrorMessage = message,
            generalErrorMessage = null,
        )
        return false
    }
}

private fun PlaceSelection.toSelectableLocation(regionCatalog: RegionCatalog?): Location? {
    val catalog = regionCatalog ?: return null
    val countryCode = countryCode ?: return null
    val suggestion = suggestedRegion
    val location = when {
        countryCode == KoreaCountryCode && suggestion?.provinceCode != null &&
            suggestion.districtCode != null -> {
            val provinceCode = "$KoreanProvincePrefix${suggestion.provinceCode}"
            catalog.findDistrict(provinceCode, suggestion.districtCode)
                ?: catalog.findDistrict(provinceCode, suggestion.districtCode.collapseCityDistrictCode())
        }
        countryCode == KoreaCountryCode -> null
        else -> catalog.findByCode(countryCode)
    }
    return location?.takeIf(Location::isSelectableTripRecordDestination)
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

// 앱 지역 목록에 없는 국가(괌 GU, 사이판 MP, 홍콩 HK, 마카오 MO 등)의 장소는 어떤 지역을 골라도
// 서버가 PLACE_COUNTRY_MISMATCH로 거절하므로 장소를 연결하지 않고 지역을 직접 고르게 한다.
private fun PlaceSelection.isOutsideRegionCatalog(regionCatalog: RegionCatalog?): Boolean {
    val catalog = regionCatalog ?: return false
    val countryCode = countryCode ?: return false
    return countryCode != KoreaCountryCode && catalog.findByCode(countryCode) == null
}

@OptIn(ExperimentalUuidApi::class)
private fun randomPlaceSessionToken(): String = Uuid.random().toString()

private const val PlaceOutsideRegionCatalogMessage =
    "이 장소가 있는 지역은 아직 앱에서 고를 수 없어요. 지역을 직접 검색해 선택해 주세요."
private const val KoreaCountryCode = "KR"
private const val KoreanProvincePrefix = "KR-"

private fun String.collapseCityDistrictCode(): String =
    if (lastOrNull()?.isDigit() == true) dropLast(1) + "0" else this

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
    if (mediaObjectKeys.isEmpty() || selectedPhotos.isEmpty()) {
        put(TripRecordEditorErrorTarget.PHOTOS, TripRecordPhotoRules.RequiredMessage)
    } else if (
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
    val dateError = TripRecordDraft(
        locationId = selectedLocation?.id ?: 0L,
        title = title,
        content = content,
        startDate = startDate,
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
        "REGION_REQUIRED",
        "PLACE_COUNTRY_MISMATCH" -> TripRecordEditorErrorTarget.LOCATION

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
