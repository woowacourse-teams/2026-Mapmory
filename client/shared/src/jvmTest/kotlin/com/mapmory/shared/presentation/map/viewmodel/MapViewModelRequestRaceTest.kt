package com.mapmory.shared.presentation.map.viewmodel

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceReference
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.PlaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class MapViewModelRequestRaceTest {
    @Test
    fun `같은_검색어_요청이_진행_중이면_중복_저장소_호출을_막는다`() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val requestGate = CompletableDeferred<Unit>()
        var searchRequests = 0
        val placeRepository = object : PlaceRepository {
            override suspend fun searchPlaces(query: String, sessionToken: String): Result<List<PlaceCandidate>> {
                searchRequests++
                requestStarted.complete(Unit)
                requestGate.await()
                return Result.success(emptyList())
            }

            override suspend fun selectPlace(placeId: String, sessionToken: String) =
                Result.failure<PlaceSelection>(error("Not used in this test"))
        }
        val viewModel = MapViewModel(emptyMapSummaryRepository(), StaticRegionCatalog(), placeRepository = placeRepository)
        viewModel.updatePlaceSearchQuery("강남구")

        val firstSearch = launch { viewModel.searchPlaces() }
        requestStarted.await()
        val duplicateSearch = launch { viewModel.searchPlaces() }
        yield()

        assertEquals(1, searchRequests)
        requestGate.complete(Unit)
        firstSearch.join()
        duplicateSearch.join()
        assertEquals(1, searchRequests)
    }

    @Test
    fun `뒤늦게_완료된_장소_검색은_나중에_선택한_장소를_덮어쓰지_않는다`() = runBlocking {
        val seoulStarted = CompletableDeferred<Unit>()
        val seoulGate = CompletableDeferred<Unit>()
        val busanStarted = CompletableDeferred<Unit>()
        val busanGate = CompletableDeferred<Unit>()
        val placeRepository = object : PlaceRepository {
            override suspend fun searchPlaces(query: String, sessionToken: String) =
                Result.success(emptyList<PlaceCandidate>())

            override suspend fun selectPlace(placeId: String, sessionToken: String): Result<PlaceSelection> {
                if (placeId == "seoul") {
                    seoulStarted.complete(Unit)
                    seoulGate.await()
                    return Result.success(placeSelection("seoul", "강남구", "11", "11680"))
                }
                busanStarted.complete(Unit)
                busanGate.await()
                return Result.success(placeSelection("busan", "부산진구", "26", "26110"))
            }
        }
        val viewModel = MapViewModel(emptyMapSummaryRepository(), StaticRegionCatalog(), placeRepository = placeRepository)
        val seoul = PlaceCandidate("seoul", "강남구", "서울특별시", null, null)
        val busan = PlaceCandidate("busan", "부산진구", "부산광역시", null, null)

        val seoulSelection = launch { viewModel.selectSearchResult(seoul) }
        seoulStarted.await()
        val busanSelection = launch { viewModel.selectSearchResult(busan) }
        busanStarted.await()

        seoulGate.complete(Unit)
        seoulSelection.join()
        assertEquals(null, viewModel.uiState.searchFocusProvinceCode)

        busanGate.complete(Unit)
        busanSelection.join()
        assertEquals("KR-26", viewModel.uiState.searchFocusProvinceCode)
        assertEquals("26110", viewModel.uiState.searchFocusRegionCode)
    }
}

private fun emptyMapSummaryRepository() = object : MapSummaryRepository {
    override suspend fun getMapSummary(tagId: Long?): Result<List<MapRegionSummary>> = Result.success(emptyList())
    override suspend fun getRootRegions(tagId: Long?): Result<List<MapRegionSummary>> = Result.success(emptyList())
    override suspend fun getChildRegions(regionId: Long, tagId: Long?): Result<List<MapRegionSummary>> = Result.success(emptyList())
}

private fun placeSelection(
    placeId: String,
    placeName: String,
    provinceCode: String,
    districtCode: String,
) = PlaceSelection(
    place = PlaceReference(placeId, placeName),
    countryCode = "KR",
    suggestedRegion = PlaceRegionSuggestion("KR", provinceCode, districtCode),
    manualRegionRequired = false,
)
