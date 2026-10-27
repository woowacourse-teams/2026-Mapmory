package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState

internal data class TripRecordPhotoCollection(
    val locationName: String,
    val records: List<TripRecordItemUiState>,
) {
    val photoCount: Int
        get() = records.sumOf { it.photoCount ?: it.photos.size }

    val photoEntries: List<Pair<TripRecordItemUiState, TripRecordPhotoUiState>>
        get() = records.flatMap { record ->
            record.photos.sortedBy(TripRecordPhotoUiState::sortOrder).map { record to it }
        }
}

internal fun groupTripRecordsByLocation(
    records: List<TripRecordItemUiState>,
): List<TripRecordPhotoCollection> = records
    .groupBy(TripRecordItemUiState::locationName)
    .map { (locationName, locationRecords) ->
        TripRecordPhotoCollection(locationName, locationRecords)
    }

internal fun groupSecondaryTripRecordCollections(
    collections: List<TripRecordPhotoCollection>,
): List<List<TripRecordPhotoCollection>> = collections.drop(1).chunked(2)
