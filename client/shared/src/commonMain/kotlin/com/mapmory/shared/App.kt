package com.mapmory.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mapmory.shared.domain.model.KoreanCountryNames
import com.mapmory.shared.domain.model.KoreanSelectableDistrictCodes
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.presentation.map.data.GeneratedKoreaMapData
import com.mapmory.shared.presentation.map.data.GeneratedKoreaDistrictMapData
import com.mapmory.shared.presentation.map.data.GeneratedWorldMapData
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.state.KoreaMapUiState
import com.mapmory.shared.presentation.map.ui.MapArtwork
import com.mapmory.shared.presentation.map.ui.KoreaMapStatusMessage
import com.mapmory.shared.presentation.triprecord.screen.TripMapScreen
import com.mapmory.shared.presentation.triprecord.screen.TripProfileScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordDetailScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordEditorScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordListScreen
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordEffect
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordAction
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordsViewModel
import kotlinx.serialization.Serializable

@Serializable
private data object MapRoute

@Serializable
private data object RecordsRoute

@Serializable
private data object CreateRoute

@Serializable
private data object ProfileRoute

@Serializable
private data class DetailRoute(
    val recordId: Long,
)

@Composable
fun MapmoryApp(
    providedRecordsViewModel: TripRecordsViewModel? = null,
    navigation: MapmoryNavigation? = null,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
) {
    val navController = rememberNavController()
    val recordsViewModel = remember(providedRecordsViewModel) {
        providedRecordsViewModel ?: TripRecordsViewModel(appLocations)
    }
    val recordsUiState = recordsViewModel.uiState
    var mapScope by remember { mutableStateOf(MapScope.KOREA) }
    var koreaMapUiState by remember { mutableStateOf<KoreaMapUiState>(KoreaMapUiState.ProvinceOverview) }
    val locationsById = remember { appLocations.associateBy(Location::id) }

    fun navigateBack(): Boolean {
        if (mapScope == MapScope.KOREA && koreaMapUiState is KoreaMapUiState.DistrictDetail) {
            koreaMapUiState = KoreaMapUiState.ProvinceOverview
            return true
        }
        if (navController.currentDestination?.id == navController.graph.startDestinationId) {
            return false
        }
        if (navController.popBackStack()) {
            return true
        }

        // popBackStack can empty the stack when a destination has no parent.
        // Restore the home route instead of leaving NavHost without content.
        navController.navigate(MapRoute) {
            launchSingleTop = true
        }
        return true
    }

    DisposableEffect(navigation, navController) {
        navigation?.bindBackHandler(::navigateBack)
        onDispose { navigation?.unbindBackHandler() }
    }

    LaunchedEffect(koreaMapUiState) {
        val loading = koreaMapUiState as? KoreaMapUiState.DistrictLoading ?: return@LaunchedEffect
        koreaMapUiState = runCatching {
            GeneratedKoreaDistrictMapData.forProvince(loading.provinceCode)
        }.fold(
            onSuccess = { regions ->
                KoreaMapUiState.DistrictDetail(loading.provinceCode, regions)
            },
            onFailure = { error ->
                KoreaMapUiState.Error(
                    provinceCode = loading.provinceCode,
                    message = error.message ?: "시·군·구 지도를 불러오지 못했습니다.",
                )
            },
        )
    }

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo<MapRoute> {
                inclusive = false
            }
            launchSingleTop = true
        }
    }

    fun handleMapLocationClick(regionCode: String) {
        val location = appLocations.firstOrNull { it.regionCode == regionCode } ?: return
        recordsViewModel.onAction(TripRecordAction.MapLocationSelected(location))
    }

    fun handleMapDistrictClick(regionCode: String, provinceCode: String) {
        val location = findMapDistrictLocation(regionCode, provinceCode, appLocations)
        location?.let { recordsViewModel.onAction(TripRecordAction.MapLocationSelected(it)) }
    }

    LaunchedEffect(recordsUiState.effect) {
        val effect = recordsUiState.effect ?: return@LaunchedEffect
        when (effect) {
            TripRecordEffect.OpenRecords -> navigateToTab(RecordsRoute)
            TripRecordEffect.OpenEditor -> navController.navigate(CreateRoute)
            is TripRecordEffect.OpenDetail -> {
                val replaced = effect.replaceCurrent && navController.popBackStack()
                if (!replaced) navController.navigate(DetailRoute(effect.recordId))
            }
            TripRecordEffect.CloseDetail -> {
                if (!navController.popBackStack()) navigateToTab(RecordsRoute)
            }
        }
        recordsViewModel.onAction(TripRecordAction.EffectHandled)
    }

    val visitedLocations = recordsUiState.records.mapNotNull { record ->
        appLocations.firstOrNull { it.name == record.locationName }
    }
    val visitedCountryCodes = visitedLocations.map { location ->
        if (location.countryId == 1L) "KR" else location.regionCode
    }.toSet()
    val visitedRegionCodes = visitedLocations.mapNotNull { location ->
        when {
            location.countryId != 1L -> null
            location.type == LocationType.PROVINCE -> location.regionCode
            else -> locationsById[location.parentId]?.regionCode
        }
    }.toSet()
    val selectedProvinceAppCode = when (val state = koreaMapUiState) {
        is KoreaMapUiState.DistrictLoading -> state.provinceCode
        is KoreaMapUiState.DistrictDetail -> state.provinceCode
        is KoreaMapUiState.Error -> state.provinceCode
        KoreaMapUiState.ProvinceOverview -> null
    }
    val selectedProvinceVisitedCount = selectedProvinceAppCode?.let { provinceCode ->
        visitedLocations.count { location ->
            location.type == LocationType.DISTRICT &&
                locationsById[location.parentId]?.regionCode == provinceCode
        }
    }

    NavHost(
        navController = navController,
        startDestination = MapRoute,
    ) {
        composable<MapRoute> {
            TripMapScreen(
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                mapScope = mapScope,
                visitedCount = when {
                    mapScope == MapScope.WORLD -> visitedCountryCodes.size
                    selectedProvinceVisitedCount != null -> selectedProvinceVisitedCount
                    else -> visitedRegionCodes.size
                },
                onMapScopeChange = {
                    mapScope = it
                    koreaMapUiState = KoreaMapUiState.ProvinceOverview
                },
                mapContent = {
                    when (mapScope) {
                        MapScope.WORLD -> MapArtwork(
                            scope = MapScope.WORLD,
                            visitedCountryCodes = visitedCountryCodes,
                            onCountryClick = ::handleMapLocationClick,
                        )

                        MapScope.KOREA -> when (val state = koreaMapUiState) {
                            KoreaMapUiState.ProvinceOverview -> MapArtwork(
                                scope = MapScope.KOREA,
                                visitedRegionCodes = visitedRegionCodes,
                                koreaRegions = GeneratedKoreaMapData.provinces,
                                onRegionClick = { provinceCode ->
                                    koreaMapUiState = KoreaMapUiState.DistrictLoading(provinceCode)
                                },
                            )

                            is KoreaMapUiState.DistrictLoading -> KoreaMapStatusMessage(
                                "${state.provinceCode} 시·군·구 지도를 불러오는 중...",
                            )

                            is KoreaMapUiState.Error -> KoreaMapStatusMessage(
                                message = state.message,
                                actionLabel = "다시 시도",
                                onAction = {
                                    koreaMapUiState = KoreaMapUiState.DistrictLoading(state.provinceCode)
                                },
                            )

                            is KoreaMapUiState.DistrictDetail -> {
                                val visitedCodes = if (selectedProvinceAppCode == null) {
                                    visitedRegionCodes
                                } else {
                                    visitedLocations
                                        .filter { location ->
                                            location.type == LocationType.DISTRICT &&
                                                locationsById[location.parentId]?.regionCode == selectedProvinceAppCode
                                        }
                                        .map { it.regionCode }
                                        .toSet()
                                }

                                MapArtwork(
                                    scope = MapScope.KOREA,
                                    visitedRegionCodes = visitedCodes,
                                    koreaRegions = state.regions,
                                    showRegionLabels = true,
                                    onRegionClick = { regionCode ->
                                        handleMapDistrictClick(regionCode, state.provinceCode)
                                    },
                                )
                            }
                        }
                    }
                },
                onBackClick = {},
                mapDetailTitle = selectedProvinceAppCode?.let { code ->
                    GeneratedKoreaMapData.provinces.firstOrNull { it.code == code }?.name
                },
                mapDetailTotal = (koreaMapUiState as? KoreaMapUiState.DistrictDetail)?.regions?.size,
                onMapDetailBackClick = { koreaMapUiState = KoreaMapUiState.ProvinceOverview },
                onRecordClick = { navigateToTab(RecordsRoute) },
                onCreateClick = {
                    recordsViewModel.onAction(TripRecordAction.StartCreating())
                },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }

        composable<RecordsRoute> {
            TripRecordListScreen(
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                uiState = TripRecordListUiState.Success(
                    records = recordsUiState.visibleRecords,
                    page = 0,
                    totalPages = 1,
                ),
                filter = recordsUiState.filter,
                locations = appLocations,
                onKeywordChanged = { keyword ->
                    recordsViewModel.onAction(TripRecordAction.KeywordChanged(keyword))
                },
                onLocationChanged = { locationId ->
                    recordsViewModel.onAction(TripRecordAction.LocationFilterChanged(locationId))
                },
                onSearchClick = {},
                onPreviousPageClick = {},
                onNextPageClick = {},
                onCreateClick = {
                    recordsViewModel.onAction(TripRecordAction.StartCreating())
                },
                onMapClick = { navigateToTab(MapRoute) },
                onRecordClick = { recordId ->
                    recordsViewModel.onAction(TripRecordAction.RecordSelected(recordId))
                },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }

        composable<CreateRoute> {
            TripRecordEditorScreen(
                modifier = Modifier.windowInsetsPadding(
                    contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                ),
                uiState = recordsUiState.editor,
                locations = appLocations,
                onLocationSelected = { location ->
                    recordsViewModel.onAction(TripRecordAction.LocationSelected(location))
                },
                onLocationTouched = {
                    recordsViewModel.onAction(TripRecordAction.LocationTouched)
                },
                onTitleChanged = { title ->
                    recordsViewModel.onAction(TripRecordAction.TitleChanged(title))
                },
                onContentChanged = { content ->
                    recordsViewModel.onAction(TripRecordAction.ContentChanged(content))
                },
                onStartDateChanged = { date ->
                    recordsViewModel.onAction(TripRecordAction.StartDateChanged(date))
                },
                onEndDateChanged = { date ->
                    recordsViewModel.onAction(TripRecordAction.EndDateChanged(date))
                },
                onPhotosAdded = { photos ->
                    recordsViewModel.onAction(TripRecordAction.PhotosAdded(photos))
                },
                onPhotoRemoved = { photoId ->
                    recordsViewModel.onAction(TripRecordAction.PhotoRemoved(photoId))
                },
                onSaveClick = { recordsViewModel.onAction(TripRecordAction.Save) },
                onBackClick = { navigateBack() },
                onMapClick = { navigateToTab(MapRoute) },
                onRecordClick = { navigateToTab(RecordsRoute) },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }

        composable<ProfileRoute> {
            TripProfileScreen(
                modifier = Modifier.windowInsetsPadding(contentWindowInsets),
                onMapClick = { navigateToTab(MapRoute) },
                onRecordClick = { navigateToTab(RecordsRoute) },
                onCreateClick = {
                    recordsViewModel.onAction(TripRecordAction.StartCreating())
                },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }

        composable<DetailRoute> { backStackEntry ->
            val detailRoute = backStackEntry.toRoute<DetailRoute>()
            val selectedRecord = recordsUiState.records.firstOrNull { it.id == detailRoute.recordId }
            TripRecordDetailScreen(
                modifier = Modifier.windowInsetsPadding(
                    contentWindowInsets.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
                uiState = selectedRecord?.let(TripRecordDetailUiState::Success)
                    ?: TripRecordDetailUiState.Error("여행 기록을 찾을 수 없습니다."),
                onBackClick = { navigateBack() },
                onEditClick = {
                    recordsViewModel.onAction(TripRecordAction.StartEditing(detailRoute.recordId))
                },
                onDeleteClick = {
                    recordsViewModel.onAction(TripRecordAction.Delete(detailRoute.recordId))
                },
                onMapClick = { navigateToTab(MapRoute) },
                onRecordClick = { navigateToTab(RecordsRoute) },
                onCreateClick = {
                    recordsViewModel.onAction(TripRecordAction.StartCreating())
                },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }
    }
}

fun createTripRecordsViewModel(): TripRecordsViewModel = TripRecordsViewModel(appLocations)

internal fun findMapDistrictLocation(
    regionCode: String,
    provinceCode: String,
    locations: List<Location>,
): Location? {
    val locationsById = locations.associateBy(Location::id)
    return locations.firstOrNull { location ->
        location.type == LocationType.DISTRICT &&
            location.regionCode == regionCode &&
            locationsById[location.parentId]?.regionCode == provinceCode
    }
}

internal fun findMapDistrictLocation(
    mapRegionName: String,
    districtLocations: List<Location>,
): Location? {
    val normalizedMapRegionName = normalizeMapRegionName(mapRegionName)
    return districtLocations.singleOrNull { location ->
        normalizeMapRegionName(location.name) == normalizedMapRegionName
    }
}

private fun normalizeMapRegionName(name: String): String {
    val compactName = name.replace(" ", "")
    val provincePrefixes = listOf(
        "서울특별시",
        "부산광역시",
        "대구광역시",
        "인천광역시",
        "광주광역시",
        "대전광역시",
        "울산광역시",
        "세종특별자치시",
        "경기도",
        "강원특별자치도",
        "강원도",
        "충청북도",
        "충청남도",
        "전북특별자치도",
        "전라북도",
        "전라남도",
        "경상북도",
        "경상남도",
        "제주특별자치도",
    )
    return provincePrefixes.firstOrNull(compactName::startsWith)
        ?.let(compactName::removePrefix)
        ?: compactName
}

private val appLocations = buildList {
    add(
        Location(
            id = 1L,
            countryId = 1L,
            parentId = null,
            regionCode = "KR-11",
            name = "서울특별시",
            type = LocationType.PROVINCE,
        ),
    )
    listOf(
        4L to ("KR-26" to "부산광역시"),
        5L to ("KR-27" to "대구광역시"),
        6L to ("KR-28" to "인천광역시"),
        7L to ("KR-29" to "광주광역시"),
        8L to ("KR-30" to "대전광역시"),
        9L to ("KR-31" to "울산광역시"),
        10L to ("KR-50" to "세종특별자치시"),
        11L to ("KR-41" to "경기도"),
        12L to ("KR-42" to "강원특별자치도"),
        13L to ("KR-43" to "충청북도"),
        14L to ("KR-44" to "충청남도"),
        15L to ("KR-45" to "전북특별자치도"),
        16L to ("KR-46" to "전라남도"),
        17L to ("KR-47" to "경상북도"),
        18L to ("KR-48" to "경상남도"),
        19L to ("KR-49" to "제주특별자치도"),
    ).forEach { (id, region) ->
        add(
            Location(
                id = id,
                countryId = 1L,
                parentId = null,
                regionCode = region.first,
                name = region.second,
                type = LocationType.PROVINCE,
            ),
        )
    }

    val koreaProvinceIds = filter {
        it.countryId == 1L && it.type == LocationType.PROVINCE
    }.associate { it.regionCode to it.id }

    KoreanSelectableDistrictCodes.forEachIndexed { index, district ->
        val id = when (district.code) {
            "11650" -> 3L
            "11680" -> 2L
            else -> 20_000L + index
        }
        add(
            Location(
                id = id,
                countryId = 1L,
                parentId = district.provinceCode?.let { koreaProvinceIds[it] },
                regionCode = district.code,
                name = normalizeMapRegionName(district.name),
                type = LocationType.DISTRICT,
            ),
        )
    }

    GeneratedWorldMapData.countries.forEachIndexed { index, country ->
        val id = 10_000L + index
        add(
            Location(
                id = id,
                countryId = id,
                parentId = null,
                regionCode = country.code,
                name = KoreanCountryNames.byCode[country.code] ?: country.name,
                type = LocationType.PROVINCE,
            ),
        )
    }
}
