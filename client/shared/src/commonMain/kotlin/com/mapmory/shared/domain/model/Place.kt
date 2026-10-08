package com.mapmory.shared.domain.model

data class PlaceCandidate(
    val placeId: String,
    val name: String,
    val address: String?,
    val attribution: String?,
    val attributionUrl: String?,
)

data class PlaceReference(
    val placeId: String,
    val name: String,
    val address: String? = null,
    val attribution: String? = null,
    val attributionUrl: String? = null,
)

data class PlaceRegionSuggestion(
    val countryCode: String,
    val provinceCode: String? = null,
    val districtCode: String? = null,
)

data class PlaceSelection(
    val place: PlaceReference,
    val countryCode: String,
    val suggestedRegion: PlaceRegionSuggestion?,
    val manualRegionRequired: Boolean,
)
