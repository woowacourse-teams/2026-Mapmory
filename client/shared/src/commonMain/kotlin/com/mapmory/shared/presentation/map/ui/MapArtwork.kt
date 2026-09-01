package com.mapmory.shared.presentation.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.domain.ProvincePolygon
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewVisitedCountries

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

@Preview(
    name = "세계 지도 아트워크",
    showBackground = true,
    widthDp = 412,
    heightDp = 500,
)
@Composable
fun WorldMapArtworkPreview() {
    PreviewSurface {
        MapArtwork(
            scope = MapScope.WORLD,
            visitedCountryCodes = previewVisitedCountries,
        )
    }
}
