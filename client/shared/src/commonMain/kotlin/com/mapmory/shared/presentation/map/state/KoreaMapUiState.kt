package com.mapmory.shared.presentation.map.state

import com.mapmory.shared.presentation.map.domain.ProvincePolygon

sealed interface KoreaMapUiState {
    data object ProvinceOverview : KoreaMapUiState

    data class DistrictLoading(val provinceCode: String) : KoreaMapUiState

    data class DistrictDetail(
        val provinceCode: String,
        val regions: List<ProvincePolygon>,
    ) : KoreaMapUiState

    data class Error(
        val provinceCode: String,
        val message: String,
    ) : KoreaMapUiState
}
