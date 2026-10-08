package com.mapmory.shared.presentation.triprecord.state

sealed interface TripRecordListUiState {
    data object Idle : TripRecordListUiState

    data object Loading : TripRecordListUiState

    data class Success(
        val records: List<TripRecordItemUiState>,
        val page: Int,
        val totalPages: Int,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : TripRecordListUiState

    data class Error(
        val message: String,
    ) : TripRecordListUiState
}
