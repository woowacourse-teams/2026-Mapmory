package com.mapmory.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.presentation.map.data.GeneratedWorldMapData
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.ui.MapArtwork
import com.mapmory.shared.presentation.triprecord.screen.TripMapScreen
import com.mapmory.shared.presentation.triprecord.screen.TripProfileScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordDetailScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordEditorScreen
import com.mapmory.shared.presentation.triprecord.screen.TripRecordListScreen
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
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
fun MapmoryApp() {
    val navController = rememberNavController()
    var mapScope by remember { mutableStateOf(MapScope.WORLD) }
    var records by remember { mutableStateOf(emptyList<TripRecordData>()) }
    var query by remember { mutableStateOf(TripRecordQuery()) }
    var editorState by remember {
        mutableStateOf(
            TripRecordEditorUiState(
                selectedLocation = appLocations.firstOrNull { it.type == LocationType.DISTRICT },
            ),
        )
    }

    val locationsById = remember { appLocations.associateBy(Location::id) }

    fun mapLocationContains(selected: Location, recordLocation: Location): Boolean {
        return when {
            selected.regionCode == "KOR" -> recordLocation.countryId == 1L || recordLocation.regionCode == "KOR"
            selected.countryId == 1L && selected.type == LocationType.PROVINCE ->
                recordLocation.id == selected.id || recordLocation.parentId == selected.id
            else -> recordLocation.id == selected.id
        }
    }

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openCreateScreen(selectedLocation: Location? = null) {
        editorState = TripRecordEditorUiState(
            selectedLocation = selectedLocation
                ?: appLocations.firstOrNull { it.type == LocationType.DISTRICT },
        )
        navController.navigate(CreateRoute)
    }

    fun handleMapLocationClick(scope: MapScope, regionCode: String) {
        val location = appLocations.firstOrNull { it.regionCode == regionCode } ?: return
        val hasRecords = records.any { record ->
            locationsById[record.locationId]?.let { recordLocation ->
                mapLocationContains(location, recordLocation)
            } == true
        }
        if (hasRecords) {
            query = TripRecordQuery(locationId = location.id)
            navigateToTab(RecordsRoute)
        } else {
            openCreateScreen(selectedLocation = location)
        }
    }

    fun openEditScreen(record: TripRecordData) {
        val location = appLocations.firstOrNull { it.id == record.locationId }
            ?: appLocations.firstOrNull { it.type == LocationType.DISTRICT }
        editorState = TripRecordEditorUiState(
            recordId = record.id,
            selectedLocation = location,
            title = record.title,
            content = record.content,
            startDate = record.startDate.orEmpty(),
            endDate = record.endDate.orEmpty(),
        )
        navController.navigate(CreateRoute)
    }

    fun saveEditor() {
        val state = editorState
        val location = state.selectedLocation
        when {
            location == null -> editorState = state.copy(errorMessage = "장소를 선택해 주세요.")
            state.title.isBlank() -> editorState = state.copy(errorMessage = "제목을 입력해 주세요.")
            else -> {
                val previousRecord = records.firstOrNull { it.id == state.recordId }
                val record = TripRecordData(
                    id = previousRecord?.id ?: ((records.maxOfOrNull(TripRecordData::id) ?: 0L) + 1L),
                    memberId = 1L,
                    locationId = location.id,
                    title = state.title.trim(),
                    content = state.content.trim(),
                    startDate = state.startDate.ifBlank { null },
                    endDate = state.endDate.ifBlank { null },
                    media = previousRecord?.media.orEmpty(),
                    createdAt = previousRecord?.createdAt.orEmpty(),
                    updatedAt = previousRecord?.updatedAt.orEmpty(),
                )
                records = if (previousRecord == null) {
                    records + record
                } else {
                    records.map { if (it.id == record.id) record else it }
                }
                query = TripRecordQuery()
                if (!navController.popBackStack()) {
                    navigateToTab(RecordsRoute)
                }
            }
        }
    }

    val visitedLocations = records.mapNotNull { record -> locationsById[record.locationId] }
    val visitedCountryCodes = visitedLocations.map { location ->
        if (location.countryId == 1L) "KOR" else location.regionCode
    }.toSet()
    val visitedRegionCodes = visitedLocations.mapNotNull { location ->
        when {
            location.countryId != 1L -> null
            location.type == LocationType.PROVINCE -> location.regionCode
            else -> locationsById[location.parentId]?.regionCode
        }
    }.toSet()

    val selectedFilterLocation = query.locationId?.let { locationsById[it] }
    val visibleRecords = records.filter { record ->
        val recordLocation = locationsById[record.locationId]
        val matchesLocation = when {
            selectedFilterLocation == null -> true
            recordLocation == null -> false
            else -> mapLocationContains(selectedFilterLocation, recordLocation)
        }
        matchesLocation &&
            (query.keyword.isNullOrBlank() ||
                record.title.contains(query.keyword.orEmpty(), ignoreCase = true) ||
                record.content.contains(query.keyword.orEmpty(), ignoreCase = true))
    }

    NavHost(
        navController = navController,
        startDestination = MapRoute,
    ) {
        composable<MapRoute> {
            TripMapScreen(
                mapScope = mapScope,
                visitedCount = if (mapScope == MapScope.WORLD) {
                    visitedCountryCodes.size
                } else {
                    visitedRegionCodes.size
                },
                onMapScopeChange = { mapScope = it },
                mapContent = {
                    // Map taps are resolved to a location and routed to records or the editor.
                    MapArtwork(
                        scope = mapScope,
                        visitedCountryCodes = visitedCountryCodes,
                        visitedRegionCodes = visitedRegionCodes,
                        onCountryClick = { countryCode ->
                            handleMapLocationClick(MapScope.WORLD, countryCode)
                        },
                        onRegionClick = { regionCode ->
                            handleMapLocationClick(MapScope.KOREA, regionCode)
                        },
                    )
                },
                onBackClick = {},
                onRecordClick = { navigateToTab(RecordsRoute) },
                onCreateClick = { openCreateScreen() },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }

        composable<RecordsRoute> {
            TripRecordListScreen(
                uiState = TripRecordListUiState.Success(
                    records = visibleRecords,
                    page = 0,
                    totalPages = 1,
                ),
                query = query,
                locations = appLocations,
                onKeywordChanged = { keyword ->
                    query = query.copy(keyword = keyword.ifBlank { null }, page = 0)
                },
                onLocationChanged = { locationId ->
                    query = query.copy(locationId = locationId, page = 0)
                },
                onSearchClick = {},
                onPreviousPageClick = {},
                onNextPageClick = {},
                onCreateClick = { openCreateScreen() },
                onMapClick = { navigateToTab(MapRoute) },
                onRecordClick = { recordId ->
                    navController.navigate(DetailRoute(recordId))
                },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }

        composable<CreateRoute> {
            TripRecordEditorScreen(
                uiState = editorState,
                locations = appLocations,
                onProvinceChanged = {},
                onLocationSelected = { location ->
                    editorState = editorState.copy(selectedLocation = location, errorMessage = null)
                },
                onTitleChanged = { title ->
                    editorState = editorState.copy(title = title, errorMessage = null)
                },
                onContentChanged = { content ->
                    editorState = editorState.copy(content = content, errorMessage = null)
                },
                onStartDateChanged = { date ->
                    editorState = editorState.copy(startDate = date, errorMessage = null)
                },
                onEndDateChanged = { date ->
                    editorState = editorState.copy(endDate = date, errorMessage = null)
                },
                onSaveClick = ::saveEditor,
                onBackClick = { navController.popBackStack() },
                onMapClick = { navigateToTab(MapRoute) },
                onRecordClick = { navigateToTab(RecordsRoute) },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }

        composable<ProfileRoute> {
            TripProfileScreen(
                onMapClick = { navigateToTab(MapRoute) },
                onRecordClick = { navigateToTab(RecordsRoute) },
                onCreateClick = { openCreateScreen() },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }

        composable<DetailRoute> { backStackEntry ->
            val detailRoute = backStackEntry.toRoute<DetailRoute>()
            val selectedRecord = records.firstOrNull { it.id == detailRoute.recordId }
            TripRecordDetailScreen(
                uiState = selectedRecord?.let(TripRecordDetailUiState::Success)
                    ?: TripRecordDetailUiState.Error("여행 기록을 찾을 수 없습니다."),
                locations = appLocations,
                onBackClick = { navController.popBackStack() },
                onEditClick = { selectedRecord?.let(::openEditScreen) },
                onDeleteClick = {
                    records = records.filterNot { it.id == detailRoute.recordId }
                    navController.popBackStack()
                },
                onMapClick = { navigateToTab(MapRoute) },
                onRecordClick = { navigateToTab(RecordsRoute) },
                onCreateClick = { openCreateScreen() },
                onProfileClick = { navigateToTab(ProfileRoute) },
            )
        }
    }
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
    add(
        Location(
            id = 2L,
            countryId = 1L,
            parentId = 1L,
            regionCode = "11680",
            name = "강남구",
            type = LocationType.DISTRICT,
        ),
    )
    add(
        Location(
            id = 3L,
            countryId = 1L,
            parentId = 1L,
            regionCode = "11650",
            name = "서초구",
            type = LocationType.DISTRICT,
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

    GeneratedWorldMapData.countries.forEachIndexed { index, country ->
        val id = 10_000L + index
        add(
            Location(
                id = id,
                countryId = id,
                parentId = null,
                regionCode = country.code,
                name = country.name,
                type = LocationType.PROVINCE,
            ),
        )
    }
}
