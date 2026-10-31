package com.mapmory.shared.presentation.map.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.model.PlaceRules
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.PlaceRepository
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.presentation.map.data.GeneratedKoreaDistrictMapData
import com.mapmory.shared.presentation.map.domain.GeoPoint
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.state.KoreaMapUiState
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class MapPlaceSearchState(
    val query: String = "",
    val results: List<PlaceCandidate> = emptyList(),
    val isSearching: Boolean = false,
    val isSelecting: Boolean = false,
    val hasSearched: Boolean = false,
    val isSelectedPlace: Boolean = false,
    val errorMessage: String? = null,
)

data class MapUiState(
    val scope: MapScope = MapScope.WORLD,
    val koreaMap: KoreaMapUiState = KoreaMapUiState.ProvinceOverview,
    val rootRegions: List<MapRegionSummary> = emptyList(),
    val koreaProvinces: List<MapRegionSummary> = emptyList(),
    val districtsByProvince: Map<String, List<MapRegionSummary>> = emptyMap(),
    val tags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
    val errorMessage: String? = null,
    val placeSearch: MapPlaceSearchState = MapPlaceSearchState(),
    val searchFocusCountryCode: String? = null,
    val searchFocusProvinceCode: String? = null,
    val searchFocusRegionCode: String? = null,
    val searchFocusRequest: Int = 0,
)

class MapViewModel(
    private val mapSummaryRepository: MapSummaryRepository,
    private val regionCatalog: RegionCatalog,
    private val getTags: GetTagsUseCase? = null,
    private val placeRepository: PlaceRepository? = null,
) : ViewModel() {
    var uiState by mutableStateOf(mapSummaryRepository.cachedUiState())
        private set

    var currentLocation by mutableStateOf<GeoPoint?>(null)
        private set

    fun updateCurrentLocation(point: GeoPoint) {
        currentLocation = point
    }

    private var loadedRevision: Long? = null
    private var placeSearchGeneration = 0
    private var placeSelectionGeneration = 0
    private var placeSessionToken: String? = null
    private var selectedPlaceQuery: String? = null

    fun updatePlaceSearchQuery(query: String) {
        placeSearchGeneration++
        placeSelectionGeneration++
        selectedPlaceQuery = null
        if (query.isBlank()) placeSessionToken = null
        uiState = uiState.copy(
            placeSearch = MapPlaceSearchState(query = query.take(PlaceRules.MaxQueryLength)),
        )
    }

    suspend fun searchPlaces(query: String = uiState.placeSearch.query) {
        val normalizedQuery = query.trim()
        val repository = placeRepository ?: return
        if (uiState.placeSearch.query.trim() != normalizedQuery ||
            selectedPlaceQuery == normalizedQuery ||
            !PlaceRules.isSearchableQuery(normalizedQuery)
        ) {
            return
        }

        val generation = ++placeSearchGeneration
        uiState = uiState.copy(
            placeSearch = uiState.placeSearch.copy(isSearching = true, errorMessage = null),
        )
        repository.searchPlaces(normalizedQuery, currentPlaceSessionToken()).fold(
            onSuccess = { candidates ->
                if (generation == placeSearchGeneration && uiState.placeSearch.query.trim() == normalizedQuery) {
                    uiState = uiState.copy(
                        placeSearch = uiState.placeSearch.copy(
                            results = candidates.take(PlaceRules.MaxCandidates),
                            isSearching = false,
                            hasSearched = true,
                        ),
                    )
                }
            },
            onFailure = { error ->
                if (generation == placeSearchGeneration && uiState.placeSearch.query.trim() == normalizedQuery) {
                    uiState = uiState.copy(
                        placeSearch = uiState.placeSearch.copy(
                            results = emptyList(),
                            isSearching = false,
                            hasSearched = true,
                            errorMessage = error.message ?: "장소를 검색하지 못했습니다.",
                        ),
                    )
                }
            },
        )
    }

    suspend fun selectSearchResult(candidate: PlaceCandidate): Boolean {
        val repository = placeRepository ?: return false
        placeSearchGeneration++
        val generation = ++placeSelectionGeneration
        val sessionToken = currentPlaceSessionToken()
        placeSessionToken = null
        uiState = uiState.copy(
            placeSearch = uiState.placeSearch.copy(
                results = emptyList(),
                isSearching = false,
                isSelecting = true,
                hasSearched = false,
                errorMessage = null,
            ),
        )

        return repository.selectPlace(candidate.placeId, sessionToken).fold(
            onSuccess = { selection ->
                if (generation != placeSelectionGeneration) return@fold false
                val focus = selection.toMapSearchFocus(regionCatalog)
                if (focus == null) {
                    uiState = uiState.copy(
                        placeSearch = uiState.placeSearch.copy(
                            isSelecting = false,
                            errorMessage = "검색한 장소의 지역 정보를 지도에서 찾지 못했습니다.",
                        ),
                    )
                    return@fold false
                }

                uiState = uiState.copy(
                    scope = focus.scope,
                    placeSearch = MapPlaceSearchState(query = candidate.name, isSelectedPlace = true),
                    searchFocusCountryCode = focus.countryCode,
                    searchFocusProvinceCode = focus.provinceCode,
                    searchFocusRegionCode = focus.regionCode,
                    searchFocusRequest = uiState.searchFocusRequest + 1,
                )
                selectedPlaceQuery = candidate.name.trim()
                if (focus.scope == MapScope.KOREA && focus.provinceCode != null) {
                    openProvince(focus.provinceCode)
                } else {
                    closeProvince()
                }
                true
            },
            onFailure = { error ->
                if (generation == placeSelectionGeneration) {
                    uiState = uiState.copy(
                        placeSearch = uiState.placeSearch.copy(
                            isSelecting = false,
                            errorMessage = error.message ?: "장소 정보를 불러오지 못했습니다.",
                        ),
                    )
                }
                false
            },
        )
    }

    private fun currentPlaceSessionToken(): String =
        placeSessionToken ?: randomPlaceSessionToken().also { placeSessionToken = it }

    suspend fun refreshIfNeeded(dataRevision: Long) {
        if (loadedRevision == dataRevision && uiState.errorMessage == null) return
        refresh()
        if (uiState.errorMessage == null) loadedRevision = dataRevision
    }

    suspend fun refresh() {
        val openProvinceCode = when (val mapState = uiState.koreaMap) {
            is KoreaMapUiState.DistrictLoading -> mapState.provinceCode
            is KoreaMapUiState.DistrictDetail -> mapState.provinceCode
            is KoreaMapUiState.Error -> mapState.provinceCode
            KoreaMapUiState.ProvinceOverview -> null
        }
        // 태그 목록 조회 실패가 기존 지도 자체를 막지 않도록 마지막 성공 목록으로 계속 그린다.
        val tags = getTags?.invoke()?.getOrElse { uiState.tags } ?: uiState.tags
        val selectedTagId = uiState.selectedTagId?.takeIf { selectedId ->
            tags.any { it.id == selectedId }
        }
        val roots = mapSummaryRepository.getRootRegions(selectedTagId).getOrElse { error ->
            uiState = uiState.copy(
                errorMessage = error.message ?: "지도 기록을 불러오지 못했습니다.",
            )
            return
        }
        val korea = roots.firstOrNull { it.code == KoreaCountryCode }
        val provinces = korea?.let { root ->
            mapSummaryRepository.getChildRegions(root.regionId, selectedTagId).getOrElse { error ->
                uiState = uiState.copy(
                    rootRegions = roots,
                    errorMessage = error.message ?: "대한민국 지역 기록을 불러오지 못했습니다.",
                )
                return
            }
        }.orEmpty()
        val districtsByProvince = mutableMapOf<String, List<MapRegionSummary>>()
        provinces.filter { it.count > 0 }.forEach { province ->
            val provinceCode = "$KoreanProvincePrefix${province.code}"
            val districts = mapSummaryRepository.getChildRegions(province.regionId, selectedTagId).getOrElse {
                mapSummaryRepository.getCachedChildRegions(province.regionId, selectedTagId) ?: return@forEach
            }
            districtsByProvince[provinceCode] = districts
        }

        uiState = uiState.copy(
            rootRegions = roots,
            koreaProvinces = provinces,
            districtsByProvince = districtsByProvince,
            tags = tags,
            selectedTagId = selectedTagId,
            errorMessage = null,
        )
        openProvinceCode?.let { provinceCode -> openProvince(provinceCode) }
    }

    fun changeScope(scope: MapScope) {
        uiState = uiState.copy(scope = scope)
    }

    suspend fun selectTag(tagId: Long?) {
        if (tagId != null && uiState.tags.none { it.id == tagId }) return
        if (uiState.selectedTagId == tagId) return
        uiState = uiState.copy(selectedTagId = tagId)
        refresh()
    }

    suspend fun openProvince(provinceCode: String) {
        val visibleDetail = uiState.koreaMap as? KoreaMapUiState.DistrictDetail
        if (visibleDetail?.provinceCode != provinceCode) {
            uiState = uiState.copy(koreaMap = KoreaMapUiState.DistrictLoading(provinceCode))
        }
        val serverProvinceCode = provinceCode.removePrefix(KoreanProvincePrefix)
        val province = uiState.koreaProvinces.firstOrNull { it.code == serverProvinceCode }
        val summaries = uiState.districtsByProvince[provinceCode] ?: province?.let { summary ->
            mapSummaryRepository.getChildRegions(summary.regionId, uiState.selectedTagId).getOrElse { error ->
                uiState = uiState.copy(
                    koreaMap = KoreaMapUiState.Error(
                        provinceCode = provinceCode,
                        message = error.message ?: "지역별 기록을 불러오지 못했습니다.",
                    ),
                )
                return
            }
        }.orEmpty()
        uiState = uiState.copy(
            districtsByProvince = uiState.districtsByProvince + (provinceCode to summaries),
        )

        val result = runCatching {
            GeneratedKoreaDistrictMapData.forProvince(provinceCode)
        }

        uiState = result.fold(
            onSuccess = { regions ->
                uiState.copy(
                    koreaMap = KoreaMapUiState.DistrictDetail(provinceCode, regions),
                    errorMessage = null,
                )
            },
            onFailure = { error ->
                uiState.copy(
                    koreaMap = KoreaMapUiState.Error(
                        provinceCode = provinceCode,
                        message = error.message ?: "시·군·구 지도를 불러오지 못했습니다.",
                    ),
                )
            },
        )
    }

    fun closeProvince(): Boolean {
        if (uiState.koreaMap == KoreaMapUiState.ProvinceOverview) return false
        uiState = uiState.copy(koreaMap = KoreaMapUiState.ProvinceOverview)
        return true
    }

    fun hasRecords(location: Location): Boolean = when {
        location.regionCode.length == CountryCodeLength ->
            uiState.rootRegions.any { it.code == location.regionCode }

        location.type == LocationType.PROVINCE ->
            uiState.koreaProvinces.any {
                it.code == location.regionCode.removePrefix(KoreanProvincePrefix)
            }

        else -> {
            val provinceCode = location.parentId
                ?.let(regionCatalog::findById)
                ?.regionCode
            uiState.districtsByProvince[provinceCode]
                .orEmpty()
                .any { it.code == location.regionCode }
        }
    }

    val visitedCountryCodes: Set<String>
        get() = uiState.rootRegions.map(MapRegionSummary::code).toSet()

    val visitedProvinceCodes: Set<String>
        get() = uiState.koreaProvinces
            .filter { it.count > 0 }
            .map { "$KoreanProvincePrefix${it.code}" }
            .toSet()

    /** 시도에 직접 저장된 기록이 있을 때만 시도 전체를 칠한다. */
    val directlyVisitedProvinceCodes: Set<String>
        get() = uiState.koreaProvinces.mapNotNull { province ->
            val provinceCode = "$KoreanProvincePrefix${province.code}"
            val districts = uiState.districtsByProvince[provinceCode] ?: return@mapNotNull null
            val districtRecordCount = districts.sumOf(MapRegionSummary::count)
            provinceCode.takeIf { province.count > districtRecordCount }
        }.toSet()

    fun visitedDistrictCodes(provinceCode: String): Set<String> =
        uiState.districtsByProvince[provinceCode]
            .orEmpty()
            .filter { it.count > 0 }
            .map(MapRegionSummary::code)
            .toSet()

    val visitedKoreaDistrictCodes: Set<String>
        get() = uiState.districtsByProvince.values.flatten()
            .filter { it.count > 0 }
            .map(MapRegionSummary::code)
            .toSet()

    val visitedKoreaMapRegionCodes: Set<String>
        get() = visitedKoreaDistrictCodes + directlyVisitedProvinceCodes

    val koreaDistrictCount: Int
        get() = regionCatalog.locations.count { it.type == LocationType.DISTRICT }

    fun visitedDistrictCount(provinceCode: String): Int = visitedDistrictCodes(provinceCode).size
}

private fun MapSummaryRepository.cachedUiState(): MapUiState {
    val roots = getCachedRootRegions().orEmpty()
    val korea = roots.firstOrNull { region -> region.code == KoreaCountryCode }
    val provinces = korea?.let { region -> getCachedChildRegions(region.regionId) }.orEmpty()
    val districtsByProvince = provinces.mapNotNull { province ->
        getCachedChildRegions(province.regionId)?.let { districts ->
            "$KoreanProvincePrefix${province.code}" to districts
        }
    }.toMap()
    return MapUiState(
        rootRegions = roots,
        koreaProvinces = provinces,
        districtsByProvince = districtsByProvince,
    )
}

private data class MapSearchFocus(
    val scope: MapScope,
    val countryCode: String,
    val provinceCode: String? = null,
    val regionCode: String? = null,
)

private fun PlaceSelection.toMapSearchFocus(regionCatalog: RegionCatalog): MapSearchFocus? {
    val countryCode = countryCode ?: suggestedRegion?.countryCode ?: return null
    if (countryCode != KoreaCountryCode) {
        return if (regionCatalog.findByCode(countryCode) != null) {
            MapSearchFocus(scope = MapScope.WORLD, countryCode = countryCode)
        } else {
            null
        }
    }

    val provinceCode = suggestedRegion?.provinceCode?.toKoreanProvinceCode()
    val districtCode = suggestedRegion?.districtCode
    val district = if (provinceCode != null && !districtCode.isNullOrBlank()) {
        regionCatalog.findDistrict(provinceCode, districtCode)
            ?: regionCatalog.findDistrict(provinceCode, districtCode.collapseCityDistrictCode())
    } else {
        null
    }
    if (district != null && provinceCode != null) {
        return MapSearchFocus(
            scope = MapScope.KOREA,
            countryCode = KoreaCountryCode,
            provinceCode = provinceCode,
            regionCode = district.regionCode,
        )
    }

    val province = provinceCode?.takeIf { regionCatalog.findByCode(it) != null }
    return MapSearchFocus(
        scope = MapScope.KOREA,
        countryCode = KoreaCountryCode,
        regionCode = province,
    )
}

private fun String.toKoreanProvinceCode(): String =
    if (startsWith(KoreanProvincePrefix)) this else "$KoreanProvincePrefix$this"

private fun String.collapseCityDistrictCode(): String =
    if (lastOrNull()?.isDigit() == true) dropLast(1) + "0" else this

@OptIn(ExperimentalUuidApi::class)
private fun randomPlaceSessionToken(): String = Uuid.random().toString()

private const val KoreaCountryCode = "KR"
private const val KoreanProvincePrefix = "KR-"
private const val CountryCodeLength = 2
