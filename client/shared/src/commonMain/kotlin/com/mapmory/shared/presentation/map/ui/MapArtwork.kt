package com.mapmory.shared.presentation.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mapmory.shared.presentation.map.domain.MapScope

@Composable
fun MapArtwork(
    scope: MapScope = MapScope.WORLD,
    visitedCountryCodes: Set<String> = emptySet(),
    visitedRegionCodes: Set<String> = emptySet(),
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
            visitedRegionCodes = visitedRegionCodes,
            onRegionClick = onRegionClick,
            modifier = modifier,
        )
    }
}
