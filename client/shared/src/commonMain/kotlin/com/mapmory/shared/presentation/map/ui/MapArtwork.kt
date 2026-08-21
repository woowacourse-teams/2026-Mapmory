package com.mapmory.shared.presentation.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.domain.ProvincePolygon

@Composable
fun MapArtwork(
    scope: MapScope = MapScope.WORLD,
    visitedCountryCodes: Set<String> = emptySet(),
    visitedRegionCodes: Set<String> = emptySet(),
    koreaRegions: List<ProvincePolygon>? = null,
    showRegionLabels: Boolean = false,
    onCountryClick: (String) -> Unit = {},
    onRegionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (scope) {
        MapScope.WORLD -> WorldGlobe(
            visitedCountryCodes = visitedCountryCodes,
            onCountryClick = onCountryClick,
            modifier = modifier,
        )

        MapScope.KOREA -> KoreaMapArtwork(
            regions = koreaRegions ?: com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData.provinces,
            visitedRegionCodes = visitedRegionCodes,
            showRegionLabels = showRegionLabels,
            onRegionClick = onRegionClick,
            modifier = modifier,
        )
    }
}
