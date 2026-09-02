package com.mapmory.shared.presentation.triprecord.state

import com.mapmory.shared.domain.model.Tag

data class TripRecordFilterUiState(
    val locationId: Long? = null,
    val tags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
)
