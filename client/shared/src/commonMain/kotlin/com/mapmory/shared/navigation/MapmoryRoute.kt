package com.mapmory.shared.navigation

import kotlinx.serialization.Serializable

@Serializable
internal data object MapRoute

@Serializable
internal data class RecordsRoute(
    val locationId: Long? = null,
)

@Serializable
internal data class EditorRoute(
    val recordId: Long? = null,
    val selectedLocationId: Long? = null,
)

@Serializable
internal data object ProfileRoute

@Serializable
internal data class DetailRoute(
    val recordId: Long,
)
