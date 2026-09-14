package com.mapmory.shared.presentation.map.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.state.KoreaMapUiState
import com.mapmory.shared.presentation.map.ui.KoreaMapStatusMessage
import com.mapmory.shared.presentation.map.ui.MapArtwork
import com.mapmory.shared.presentation.map.viewmodel.MapViewModel
import com.mapmory.shared.presentation.triprecord.screen.TripMapScreen
import kotlinx.coroutines.launch

@Composable
internal fun MapRoute(
    viewModel: MapViewModel,
    regionCatalog: RegionCatalog,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    tripRecordRevision: Long,
    onOpenRecords: (Long?) -> Unit,
    onOpenEditor: (Long?) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    val scope = rememberCoroutineScope()
    val analytics = LocalMapmoryAnalytics.current
    val latestNestedBack = rememberUpdatedState { viewModel.closeProvince() }

    LaunchedEffect(Unit) {
        analytics.logEvent(
            MapmoryAnalyticsEvent.SCREEN_VIEW,
            mapOf("screen_name" to "map"),
        )
    }

    LaunchedEffect(viewModel, tripRecordRevision) {
        viewModel.refresh()
    }

    DisposableEffect(viewModel, backHandlerRegistry) {
        val registration = backHandlerRegistry.register {
            latestNestedBack.value()
        }
        onDispose {
            backHandlerRegistry.unregister(registration)
        }
    }

    fun openLocation(location: Location) {
        val hasRecords = viewModel.hasRecords(location)
        analytics.logEvent(
            MapmoryAnalyticsEvent.MAP_LOCATION_SELECTED,
            mapOf(
                "location_type" to location.type.name.lowercase(),
                "has_records" to hasRecords.toString(),
            ),
        )
        if (hasRecords) {
            onOpenRecords(location.id)
        } else {
            onOpenEditor(location.id)
        }
    }

    val selectedProvinceCode = uiState.koreaMap.provinceCodeOrNull()
    TripMapScreen(
        modifier = modifier,
        mapScope = uiState.scope,
        visitedCount = when {
            uiState.scope == MapScope.WORLD -> viewModel.visitedCountryCodes.size
            selectedProvinceCode != null -> viewModel.visitedDistrictCount(selectedProvinceCode)
            else -> viewModel.visitedProvinceCodes.size
        },
        onMapScopeChange = { selectedScope ->
            analytics.logEvent(
                MapmoryAnalyticsEvent.MAP_SCOPE_CHANGED,
                mapOf("scope" to selectedScope.name.lowercase()),
            )
            viewModel.changeScope(selectedScope)
        },
        tags = uiState.tags,
        selectedTagId = uiState.selectedTagId,
        onTagSelected = { tagId ->
            scope.launch { viewModel.selectTag(tagId) }
        },
        mapContent = {
            when (uiState.scope) {
                MapScope.WORLD -> MapArtwork(
                    scope = MapScope.WORLD,
                    visitedCountryCodes = viewModel.visitedCountryCodes,
                    onCountryClick = { countryCode ->
                        if (countryCode == KoreaCountryCode) {
                            val korea = regionCatalog.findByCode(KoreaCountryCode)
                            if (korea != null && viewModel.hasRecords(korea)) {
                                openLocation(korea)
                            } else {
                                viewModel.changeScope(MapScope.KOREA)
                            }
                        } else {
                            regionCatalog.findByCode(countryCode)?.let(::openLocation)
                        }
                    },
                )

                MapScope.KOREA -> when (val mapState = uiState.koreaMap) {
                    KoreaMapUiState.ProvinceOverview -> MapArtwork(
                        scope = MapScope.KOREA,
                        visitedRegionCodes = viewModel.visitedProvinceCodes,
                        koreaRegions = GeneratedKoreaMapData.provinces,
                        onRegionClick = { provinceCode ->
                            analytics.logEvent(
                                MapmoryAnalyticsEvent.MAP_PROVINCE_SELECTED,
                                mapOf("province_code" to provinceCode),
                            )
                            scope.launch { viewModel.openProvince(provinceCode) }
                        },
                    )

                    is KoreaMapUiState.DistrictLoading -> KoreaMapStatusMessage(
                        "${mapState.provinceCode} 시·군·구 지도를 불러오는 중...",
                    )

                    is KoreaMapUiState.Error -> KoreaMapStatusMessage(
                        message = mapState.message,
                        actionLabel = "다시 시도",
                        onAction = {
                            scope.launch { viewModel.openProvince(mapState.provinceCode) }
                        },
                    )

                    is KoreaMapUiState.DistrictDetail -> MapArtwork(
                        scope = MapScope.KOREA,
                        visitedRegionCodes = viewModel.visitedDistrictCodes(mapState.provinceCode),
                        koreaRegions = mapState.regions,
                        showRegionLabels = true,
                        onRegionClick = { districtCode ->
                            regionCatalog.findDistrict(
                                provinceCode = mapState.provinceCode,
                                districtCode = districtCode,
                            )?.let(::openLocation)
                        },
                    )
                }
            }
        },
        onBackClick = {},
        mapDetailTitle = selectedProvinceCode?.let { provinceCode ->
            GeneratedKoreaMapData.provinces.firstOrNull { it.code == provinceCode }?.name
        },
        mapDetailTotal = (uiState.koreaMap as? KoreaMapUiState.DistrictDetail)?.regions?.size,
        onMapDetailBackClick = {
            analytics.logEvent(MapmoryAnalyticsEvent.MAP_DETAIL_BACK_CLICKED)
            viewModel.closeProvince()
        },
        onRecordClick = { onOpenRecords(null) },
        onCreateClick = { onOpenEditor(null) },
        onProfileClick = onOpenProfile,
    )
}

private fun KoreaMapUiState.provinceCodeOrNull(): String? = when (this) {
    is KoreaMapUiState.DistrictLoading -> provinceCode
    is KoreaMapUiState.DistrictDetail -> provinceCode
    is KoreaMapUiState.Error -> provinceCode
    KoreaMapUiState.ProvinceOverview -> null
}

private const val KoreaCountryCode = "KR"
