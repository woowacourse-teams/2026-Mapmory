package com.mapmory.shared.presentation.triprecord.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.usecase.GetTripRecordsUseCase
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.state.toTripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoadResult
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 여행 기록 목록 화면의 상태와 조회 동작을 관리한다. */
class TripRecordListViewModel(
    private val getTripRecords: GetTripRecordsUseCase,
    private val regionCatalog: RegionCatalog? = null,
    private val thumbnailLoader: TripRecordThumbnailLoader? = null,
) : ViewModel() {
    private var isRouteInitialized = false
    private var loadGeneration = 0L

    var uiState by mutableStateOf<TripRecordListUiState>(TripRecordListUiState.Idle)
        private set

    var query by mutableStateOf(TripRecordQuery())
        private set

    fun filterByLocation(locationId: Long?) {
        query = query.copy(locationId = locationId, page = 0)
    }

    suspend fun initialize(locationId: Long?) {
        if (isRouteInitialized) return
        isRouteInitialized = true
        filterByLocation(locationId)
        load()
    }

    suspend fun refresh(locationId: Long?) {
        if (!isRouteInitialized) {
            initialize(locationId)
            return
        }
        if (query.locationId != locationId) {
            filterByLocation(locationId)
        }
        load()
    }

    suspend fun load(query: TripRecordQuery = this.query) {
        val generation = ++loadGeneration
        this.query = query
        uiState = TripRecordListUiState.Loading
        val page = getTripRecords(query).getOrElse { error ->
            if (generation == loadGeneration) {
                uiState = TripRecordListUiState.Error(
                    error.message ?: "여행 기록을 불러오지 못했습니다.",
                )
            }
            return
        }
        if (generation != loadGeneration) return

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
        locationName = record.regionName
            ?: record.locationId?.let { regionCatalog?.findById(it)?.name }
            ?: "여행지",
    )

    suspend fun previousPage() {
        if (query.page > 0) load(query.copy(page = query.page - 1))
    }

    suspend fun nextPage() {
        val state = uiState as? TripRecordListUiState.Success ?: return
        if (query.page + 1 < state.totalPages) load(query.copy(page = query.page + 1))
    }
}

private const val MaxConcurrentThumbnailLoads = 3
