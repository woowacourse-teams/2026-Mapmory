package com.mapmory.shared.presentation.map.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.region.RegionCatalog
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.presentation.map.data.GeneratedKoreaDistrictMapData
import com.mapmory.shared.presentation.map.domain.MapScope
import com.mapmory.shared.presentation.map.state.KoreaMapUiState

data class MapUiState(
    val scope: MapScope = MapScope.KOREA,
    val koreaMap: KoreaMapUiState = KoreaMapUiState.ProvinceOverview,
    val rootRegions: List<MapRegionSummary> = emptyList(),
    val koreaProvinces: List<MapRegionSummary> = emptyList(),
    val districtsByProvince: Map<String, List<MapRegionSummary>> = emptyMap(),
    val tags: List<Tag> = emptyList(),
    val selectedTagId: Long? = null,
    val errorMessage: String? = null,
)

class MapViewModel(
    private val mapSummaryRepository: MapSummaryRepository,
    private val regionCatalog: RegionCatalog,
    private val getTags: GetTagsUseCase? = null,
) : ViewModel() {
    var uiState by mutableStateOf(mapSummaryRepository.cachedUiState())
        private set

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

        uiState = uiState.copy(
            rootRegions = roots,
            koreaProvinces = provinces,
            districtsByProvince = emptyMap(),
            tags = tags,
            selectedTagId = selectedTagId,
            errorMessage = null,
        )
        openProvinceCode?.let { provinceCode -> openProvince(provinceCode) }
    }

    fun changeScope(scope: MapScope) {
        uiState = uiState.copy(
            scope = scope,
            koreaMap = KoreaMapUiState.ProvinceOverview,
        )
    }

    suspend fun selectTag(tagId: Long?) {
        if (tagId != null && uiState.tags.none { it.id == tagId }) return
        if (uiState.selectedTagId == tagId) return
        uiState = uiState.copy(selectedTagId = tagId)
        refresh()
    }

    suspend fun openProvince(provinceCode: String) {
        uiState = uiState.copy(koreaMap = KoreaMapUiState.DistrictLoading(provinceCode))
        val serverProvinceCode = provinceCode.removePrefix(KoreanProvincePrefix)
        val province = uiState.koreaProvinces.firstOrNull { it.code == serverProvinceCode }
        val summaries = province?.let { summary ->
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
            .map { "$KoreanProvincePrefix${it.code}" }
            .toSet()

    fun visitedDistrictCodes(provinceCode: String): Set<String> =
        uiState.districtsByProvince[provinceCode]
            .orEmpty()
            .map(MapRegionSummary::code)
            .toSet()

    fun visitedDistrictCount(provinceCode: String): Int = visitedDistrictCodes(provinceCode).size
}

private fun MapSummaryRepository.cachedUiState(): MapUiState {
    val roots = getCachedRootRegions().orEmpty()
    val korea = roots.firstOrNull { region -> region.code == KoreaCountryCode }
    val provinces = korea?.let { region -> getCachedChildRegions(region.regionId) }.orEmpty()
    return MapUiState(
        rootRegions = roots,
        koreaProvinces = provinces,
    )
}

private const val KoreaCountryCode = "KR"
private const val KoreanProvincePrefix = "KR-"
private const val CountryCodeLength = 2
