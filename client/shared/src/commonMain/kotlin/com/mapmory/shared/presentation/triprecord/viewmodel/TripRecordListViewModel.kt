package com.mapmory.shared.presentation.triprecord.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.usecase.GetTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordsUseCase
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoCollectionLoadState
import com.mapmory.shared.presentation.triprecord.state.toTripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoadResult
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 여행 기록 목록 화면의 상태와 조회 동작을 관리한다. */
class TripRecordListViewModel(
    private val getTripRecords: GetTripRecordsUseCase,
    private val getTripRecord: GetTripRecordUseCase,
    private val regionCatalog: RegionCatalog? = null,
    private val thumbnailLoader: TripRecordThumbnailLoader? = null,
    private val getTags: GetTagsUseCase? = null,
) : ViewModel() {
    private var isRouteInitialized = false
    private var loadGeneration = 0L
    private var collectionLoadGeneration = 0L
    private var loadedQuery: TripRecordQuery? = null
    private var loadedRevision: Long? = null

    var uiState by mutableStateOf<TripRecordListUiState>(TripRecordListUiState.Idle)
        private set

    var query by mutableStateOf(TripRecordQuery())
        private set

    val selectedLocationName: String?
        get() = query.locationId?.let { regionCatalog?.findById(it)?.name }

    var availableTags by mutableStateOf<List<Tag>>(emptyList())
        private set

    var photoCollectionLoadState by mutableStateOf<TripRecordPhotoCollectionLoadState>(
        TripRecordPhotoCollectionLoadState.Idle,
    )
        private set

    fun filterByLocation(locationId: Long?) {
        if (query.locationId != locationId) {
            collectionLoadGeneration++
            photoCollectionLoadState = TripRecordPhotoCollectionLoadState.Idle
            query = query.copy(locationId = locationId, page = 0)
        }
    }

    private fun filterByTag(tagId: Long?) {
        if (query.tagId != tagId) {
            collectionLoadGeneration++
            photoCollectionLoadState = TripRecordPhotoCollectionLoadState.Idle
            query = query.copy(tagId = tagId, page = 0)
        }
    }

    suspend fun initialize(locationId: Long?) {
        if (isRouteInitialized) return
        isRouteInitialized = true
        filterByLocation(locationId)
        load(refreshTagsFirst = true)
    }

    suspend fun refreshIfNeeded(locationId: Long?, dataRevision: Long) {
        if (loadedRevision == dataRevision && query.locationId == locationId &&
            (uiState as? TripRecordListUiState.Success)?.refreshError == null &&
            uiState is TripRecordListUiState.Success
        ) return
        if (loadedRevision != dataRevision) {
            collectionLoadGeneration++
            photoCollectionLoadState = TripRecordPhotoCollectionLoadState.Idle
        }
        refresh(locationId)
        if ((uiState as? TripRecordListUiState.Success)?.let { !it.isRefreshing && it.refreshError == null } == true) {
            loadedRevision = dataRevision
        }
    }

    suspend fun refresh(locationId: Long?) {
        if (!isRouteInitialized) {
            initialize(locationId)
            return
        }
        if (query.locationId != locationId) {
            filterByLocation(locationId)
        }
        load(refreshTagsFirst = true)
    }

    suspend fun selectTag(tagId: Long?) {
        if (tagId != null && availableTags.none { it.id == tagId }) return
        if (query.tagId == tagId) return
        filterByTag(tagId)
        load()
    }

    private suspend fun refreshTags(generation: Long) {
        val loadTags = getTags ?: return
        loadTags().onSuccess { tags ->
            if (generation != loadGeneration) return@onSuccess
            availableTags = tags
            if (query.tagId != null && tags.none { it.id == query.tagId }) {
                filterByTag(null)
            }
        }
    }

    suspend fun load(query: TripRecordQuery = this.query, refreshTagsFirst: Boolean = false) {
        val generation = ++loadGeneration
        var previousSuccess = (uiState as? TripRecordListUiState.Success)
            ?.takeIf { loadedQuery == query }
        this.query = query
        uiState = previousSuccess
            ?.copy(isRefreshing = true, refreshError = null)
            ?: TripRecordListUiState.Loading
        if (previousSuccess == null) {
            val cached = getTripRecords.cached(query)
            if (generation != loadGeneration) return
            if (cached != null) {
                loadedQuery = query
                previousSuccess = TripRecordListUiState.Success(
                    records = cached.records.map(::toUiState),
                    page = cached.page,
                    totalPages = cached.totalPages,
                    isRefreshing = true,
                )
                uiState = previousSuccess
            }
        }
        if (refreshTagsFirst) {
            refreshTags(generation)
            if (generation != loadGeneration) return
            if (this.query != query) {
                load(this.query)
                return
            }
        }
        val page = getTripRecords(query).getOrElse { error ->
            if (generation == loadGeneration) {
                uiState = previousSuccess?.copy(
                    isRefreshing = false,
                    refreshError = error.message ?: "목록 갱신 실패",
                ) ?: TripRecordListUiState.Error(
                    error.message ?: "여행 기록을 불러오지 못했습니다.",
                )
            }
            return
        }
        if (generation != loadGeneration) return

        loadedQuery = query
        uiState = TripRecordListUiState.Success(
            records = page.records.map(::toUiState),
            page = page.page,
            totalPages = page.totalPages,
        )

        val expiredRecordIds = loadThumbnails(page.records, generation)
        if (expiredRecordIds.isEmpty() || generation != loadGeneration) return

        // Presigned URL 만료 시 목록 GET을 한 번만 다시 호출해 해당 사진들만 재시도한다.
        val refreshedById = getTripRecords(query).getOrNull()
            ?.records
            ?.associateBy(TripRecordSummary::id)
            ?: return
        val expiredRecords = expiredRecordIds.mapNotNull(refreshedById::get)
        loadThumbnails(expiredRecords, generation)
    }

    private suspend fun loadThumbnails(
        records: List<TripRecordSummary>,
        generation: Long,
    ): List<Long> {
        val loader = thumbnailLoader ?: return emptyList()
        val semaphore = Semaphore(MaxConcurrentThumbnailLoads)
        return coroutineScope {
            records
                .filter { it.thumbnailUrl != null }
                .map { record ->
                    async {
                        val result = semaphore.withPermit { loader.load(record) }
                        when (result) {
                            is TripRecordThumbnailLoadResult.Success -> {
                                applyThumbnail(record, result.previewBytes, generation)
                                null
                            }
                            TripRecordThumbnailLoadResult.UrlExpired -> record.id
                            TripRecordThumbnailLoadResult.Unavailable -> null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private fun applyThumbnail(
        record: TripRecordSummary,
        previewBytes: ByteArray,
        generation: Long,
    ) {
        if (generation != loadGeneration) return
        val state = uiState as? TripRecordListUiState.Success ?: return
        val current = state.records.firstOrNull { it.id == record.id } ?: return
        val updated = record
            .copy(thumbnailPreviewBytes = previewBytes)
            .toTripRecordItemUiState(locationName = current.locationName)
        uiState = state.copy(
            records = state.records.map { item -> if (item.id == record.id) updated else item },
        )
    }

    private fun toUiState(record: TripRecordSummary) = record.toTripRecordItemUiState(
        locationName = record.resolvedLocationName(),
    )

    private fun TripRecordSummary.resolvedLocationName(): String = regionName
        ?: locationId?.let { regionCatalog?.findById(it)?.name }
        ?: "여행지"

    suspend fun loadPhotoCollection(locationName: String) {
        val currentState = photoCollectionLoadState
        if ((currentState is TripRecordPhotoCollectionLoadState.Loading && currentState.locationName == locationName) ||
            (currentState is TripRecordPhotoCollectionLoadState.Loaded && currentState.locationName == locationName)
        ) return

        val generation = ++collectionLoadGeneration
        photoCollectionLoadState = TripRecordPhotoCollectionLoadState.Loading(locationName)
        val records = try {
            val firstQuery = query.copy(page = 0, size = CollectionPageSize)
            val firstPage = getTripRecords(firstQuery).getOrThrow()
            val summaries = buildList {
                addAll(firstPage.records)
                for (page in 1 until firstPage.totalPages) {
                    addAll(getTripRecords(firstQuery.copy(page = page)).getOrThrow().records)
                }
            }.distinctBy(TripRecordSummary::id)
                .filter { it.resolvedLocationName() == locationName }
            val semaphore = Semaphore(MaxConcurrentCollectionDetailLoads)
            coroutineScope {
                summaries.map { summary ->
                    async {
                        semaphore.withPermit {
                            getTripRecord(summary.id).getOrThrow()
                                .toTripRecordItemUiState(summary.resolvedLocationName())
                        }
                    }
                }.awaitAll()
            }
        } catch (cancelled: CancellationException) {
            if (generation == collectionLoadGeneration) {
                photoCollectionLoadState = TripRecordPhotoCollectionLoadState.Idle
            }
            throw cancelled
        } catch (error: Exception) {
            if (generation == collectionLoadGeneration) {
                photoCollectionLoadState = TripRecordPhotoCollectionLoadState.Error(
                    locationName = locationName,
                    message = error.message ?: "지역 사진을 불러오지 못했습니다.",
                )
            }
            return
        }
        if (generation == collectionLoadGeneration) {
            photoCollectionLoadState = TripRecordPhotoCollectionLoadState.Loaded(locationName, records)
        }
    }

    suspend fun previousPage() {
        if (query.page > 0) load(query.copy(page = query.page - 1))
    }

    suspend fun nextPage() {
        val state = uiState as? TripRecordListUiState.Success ?: return
        if (query.page + 1 < state.totalPages) load(query.copy(page = query.page + 1))
    }
}

private const val MaxConcurrentThumbnailLoads = 3
private const val MaxConcurrentCollectionDetailLoads = 3
private const val CollectionPageSize = 100
