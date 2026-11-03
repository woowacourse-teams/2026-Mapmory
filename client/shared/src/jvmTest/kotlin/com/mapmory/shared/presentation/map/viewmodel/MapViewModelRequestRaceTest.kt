package com.mapmory.shared.presentation.map.viewmodel

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.domain.model.MapRegionLevel
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.MapRegionType
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceReference
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.PlaceRepository
import com.mapmory.shared.presentation.map.state.KoreaMapUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class MapViewModelRequestRaceTest {
    @Test
    fun `이전_검색_상세_응답은_나중에_선택한_지역을_덮어쓰지_않는다`() = runBlocking {
        val seoulStarted = CompletableDeferred<Unit>()
        val seoulGate = CompletableDeferred<Unit>()
        val busanStarted = CompletableDeferred<Unit>()
        val busanGate = CompletableDeferred<Unit>()
        val repository = mapSummaryRepository(
            onSeoulRequest = {
                seoulStarted.complete(Unit)
                seoulGate.await()
            },
            onBusanRequest = {
                busanStarted.complete(Unit)
                busanGate.await()
            },
        )
        val viewModel = MapViewModel(repository, StaticRegionCatalog(), placeRepository = placeRepository())
        val seoul = PlaceCandidate("seoul", "강남구", "서울특별시", null, null)
        val busan = PlaceCandidate("busan", "부산진구", "부산광역시", null, null)
        viewModel.refresh()

        val seoulSelection = launch { viewModel.selectSearchResult(seoul) }
        seoulStarted.await()
        val busanSelection = launch { viewModel.selectSearchResult(busan) }
        busanStarted.await()

        seoulGate.complete(Unit)
        seoulSelection.join()
        val currentLoading = viewModel.uiState.koreaMap
        assertTrue(currentLoading is KoreaMapUiState.DistrictLoading && currentLoading.provinceCode == "KR-26")

        busanGate.complete(Unit)
        busanSelection.join()
        val currentDetail = viewModel.uiState.koreaMap
        assertTrue(currentDetail is KoreaMapUiState.DistrictDetail && currentDetail.provinceCode == "KR-26")
    }

    @Test
    fun `상세_지역_로딩_중_닫으면_늦게_도착한_응답이_지도를_다시_열지_않는다`() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val requestGate = CompletableDeferred<Unit>()
        val repository = mapSummaryRepository(
            onSeoulRequest = {
                requestStarted.complete(Unit)
                requestGate.await()
            },
        )
        val viewModel = MapViewModel(repository, StaticRegionCatalog(), placeRepository = placeRepository())
        val seoul = PlaceCandidate("seoul", "강남구", "서울특별시", null, null)
        viewModel.refresh()

        val selection = launch { viewModel.selectSearchResult(seoul) }
        requestStarted.await()

        assertTrue(viewModel.closeProvince())
        assertTrue(viewModel.uiState.koreaMap == KoreaMapUiState.ProvinceOverview)
        requestGate.complete(Unit)
        selection.join()

        assertTrue(viewModel.uiState.koreaMap == KoreaMapUiState.ProvinceOverview)
    }

    private fun mapSummaryRepository(
        onSeoulRequest: suspend () -> Unit = {},
        onBusanRequest: suspend () -> Unit = {},
    ) = object : MapSummaryRepository {
        private val korea = MapRegionSummary(1, "KR", MapRegionType.COUNTRY, "대한민국", 2, MapRegionLevel.LOW)
        private val seoul = MapRegionSummary(11, "11", MapRegionType.PROVINCE, "서울특별시", 0, MapRegionLevel.LOW)
        private val busan = MapRegionSummary(26, "26", MapRegionType.PROVINCE, "부산광역시", 0, MapRegionLevel.LOW)

        override suspend fun getRootRegions(tagId: Long?) = Result.success(listOf(korea))

        override suspend fun getChildRegions(regionId: Long, tagId: Long?): Result<List<MapRegionSummary>> = when (regionId) {
            korea.regionId -> Result.success(listOf(seoul, busan))
            seoul.regionId -> {
                onSeoulRequest()
                Result.success(emptyList())
            }
            busan.regionId -> {
                onBusanRequest()
                Result.success(emptyList())
            }
            else -> Result.success(emptyList())
        }
    }

    private fun placeRepository() = object : PlaceRepository {
        override suspend fun searchPlaces(query: String, sessionToken: String) = Result.success(emptyList<PlaceCandidate>())

        override suspend fun selectPlace(placeId: String, sessionToken: String): Result<PlaceSelection> {
            val candidate = if (placeId == "seoul") {
                PlaceReference(placeId, "강남구") to PlaceRegionSuggestion("KR", "11", "11680")
            } else {
                PlaceReference(placeId, "부산진구") to PlaceRegionSuggestion("KR", "26", "26110")
            }
            return Result.success(
                PlaceSelection(
                    place = candidate.first,
                    countryCode = "KR",
                    suggestedRegion = candidate.second,
                    manualRegionRequired = false,
                ),
            )
        }
    }
}
