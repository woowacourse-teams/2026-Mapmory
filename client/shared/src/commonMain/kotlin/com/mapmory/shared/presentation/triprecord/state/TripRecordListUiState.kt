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
        val totalElements: Long = records.size.toLong(),
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : TripRecordListUiState

    data class Error(
        val message: String,
    ) : TripRecordListUiState
}

sealed interface TripRecordPhotoCollectionLoadState {
    data object Idle : TripRecordPhotoCollectionLoadState
    data class Loading(
        val locationName: String,
        val locationId: Long? = null,
    ) : TripRecordPhotoCollectionLoadState {
        val key: String get() = locationId?.let { "region:$it" } ?: "name:$locationName"
    }
    data class Loaded(
        val locationName: String,
        val records: List<TripRecordItemUiState>,
        val locationId: Long? = null,
    ) : TripRecordPhotoCollectionLoadState {
        val key: String get() = locationId?.let { "region:$it" } ?: "name:$locationName"
    }
    data class Error(
        val locationName: String,
        val message: String,
        val locationId: Long? = null,
    ) : TripRecordPhotoCollectionLoadState {
        val key: String get() = locationId?.let { "region:$it" } ?: "name:$locationName"
    }
}

sealed interface TripRecordPhotoCollectionIndexState {
    data object Idle : TripRecordPhotoCollectionIndexState
    data object Loading : TripRecordPhotoCollectionIndexState
    data class Loaded(val records: List<TripRecordItemUiState>) : TripRecordPhotoCollectionIndexState
    data class Error(val message: String) : TripRecordPhotoCollectionIndexState
}
