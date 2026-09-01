package com.mapmory.shared.presentation.triprecord.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mapmory.shared.domain.usecase.DeleteTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordUseCase
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.toTripRecordItemUiState

class TripRecordDetailViewModel(
    private val getTripRecord: GetTripRecordUseCase,
    private val deleteTripRecord: DeleteTripRecordUseCase,
    private val regionCatalog: RegionCatalog? = null,
    private val onTripRecordsChanged: () -> Unit = {},
) : ViewModel() {
    var uiState by mutableStateOf<TripRecordDetailUiState>(TripRecordDetailUiState.Idle)
        private set

    suspend fun load(id: Long) {
        uiState = TripRecordDetailUiState.Loading
        uiState = getTripRecord(id).fold(
            onSuccess = { record ->
                TripRecordDetailUiState.Success(
                    record.toTripRecordItemUiState(
                        locationName = regionCatalog?.findById(record.locationId)?.name ?: "여행지",
                    ),
                )
            },
            onFailure = { error ->
                TripRecordDetailUiState.Error(error.message ?: "여행 기록을 불러오지 못했습니다.")
            },
        )
    }

    suspend fun delete(): Boolean {
        val record = (uiState as? TripRecordDetailUiState.Success)?.record ?: return false
        uiState = TripRecordDetailUiState.Deleting
        return deleteTripRecord(record.id).fold(
            onSuccess = {
                onTripRecordsChanged()
                true
            },
            onFailure = { error ->
                uiState = TripRecordDetailUiState.Error(
                    error.message ?: "여행 기록을 삭제하지 못했습니다.",
                )
                false
            },
        )
    }
}
