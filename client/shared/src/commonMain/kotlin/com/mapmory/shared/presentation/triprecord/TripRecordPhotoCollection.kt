package com.mapmory.shared.presentation.triprecord

import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState

internal data class TripRecordPhotoCollection(
    val locationName: String,
    val records: List<TripRecordItemUiState>,
    val locationId: Long? = null,
) {
    val key: String
        get() = locationId?.let { "region:$it" } ?: "name:$locationName"

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
    .groupBy { record -> record.locationId?.let { "region:$it" } ?: "name:${record.locationName}" }
    .map { (_, locationRecords) ->
        val first = locationRecords.first()
        TripRecordPhotoCollection(first.locationName, locationRecords, first.locationId)
    }

internal fun groupSecondaryTripRecordCollections(
    collections: List<TripRecordPhotoCollection>,
): List<List<TripRecordPhotoCollection>> = collections.drop(1).chunked(2)
