package com.mapmory.shared.presentation.triprecord.viewmodel

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.PhotoRegionSuggestion
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.repository.PlaceRepository
import com.mapmory.shared.domain.usecase.CreateTripRecordUseCase
import com.mapmory.shared.domain.usecase.UpdateTripRecordUseCase
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.runSuspend
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PhotoRegionSuggestionTest {
    private val catalog = StaticRegionCatalog()
    private val daegu get() = catalog.requireByCode("KR-27")
    private val junggu get() = requireNotNull(catalog.findDistrict("KR-27", "27110"))
    private val busan get() = catalog.requireByCode("KR-26")

    @Test
    fun `취소된_조회는_같은_대표사진으로_돌아오면_다시_시도할_수_있다`() = runSuspend {
        var cancelled = true
        val repository = SuggestionRepository {
            if (cancelled) throw kotlinx.coroutines.CancellationException("화면 이탈")
            Result.success(PhotoRegionSuggestion("KR", PlaceRegionSuggestion("KR", "27", "27110"), false))
        }
        val vm = viewModel(repository, daegu)
        val photos = listOf(photo("cover", 35.0, 128.0))
        try {
            vm.suggestRegionForCover(photos)
            kotlin.test.fail("취소는 호출자에게 전달되어야 합니다")
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = false
        }
        vm.suggestRegionForCover(photos)
        assertEquals(2, repository.requests.size)
        assertEquals(junggu, vm.uiState.selectedLocation)
    }

    @Test
    fun `대표사진의_GPS로_한번만_요청하고_같은_시도의_구군을_채운다`() = runSuspend {
        val repository = SuggestionRepository()
        val vm = viewModel(repository, daegu)
        val cover = photo("cover", 35.8694, 128.6062)
        val next = photo("next", 37.0, 127.0)
        vm.suggestRegionForCover(listOf(cover, next))
        vm.suggestRegionForCover(listOf(cover, next))
        assertEquals(listOf(35.8694 to 128.6062), repository.requests)
        assertEquals(junggu, vm.uiState.selectedLocation)
        assertEquals("Google Maps", vm.uiState.photoRegionAttribution)
        assertNull(vm.uiState.selectedPlace)
    }

    @Test
    fun `대표사진에_GPS가_없으면_처음_GPS가_있는_다음_사진만_사용한다`() = runSuspend {
        val repository = SuggestionRepository()
        val vm = viewModel(repository, daegu)
        vm.suggestRegionForCover(listOf(photo("cover"), photo("next", 35.0, 128.0), photo("last", 36.0, 129.0)))
        assertEquals(listOf(35.0 to 128.0), repository.requests)
        assertEquals(junggu, vm.uiState.selectedLocation)
    }

    @Test
    fun `GPS가_없으면_요청하지_않고_선택한_지역을_유지한다`() = runSuspend {
        val repository = SuggestionRepository()
        val vm = viewModel(repository, daegu)
        vm.suggestRegionForCover(listOf(photo("one"), photo("two")))
        assertEquals(emptyList(), repository.requests)
        assertEquals(daegu, vm.uiState.selectedLocation)
    }

    @Test
    fun `대표사진이_바뀌면_새로운_대표사진으로_다시_요청한다`() = runSuspend {
        var suggestion = daeguSuggestion()
        val repository = SuggestionRepository { Result.success(suggestion) }
        val vm = viewModel(repository, daegu)
        vm.suggestRegionForCover(listOf(photo("one", 35.0, 128.0)))
        assertEquals(junggu, vm.uiState.selectedLocation)
        suggestion = suggestion.copy(suggestedRegion = PlaceRegionSuggestion("KR", "27", "27230"))
        vm.suggestRegionForCover(listOf(photo("two", 36.0, 129.0)))
        assertEquals(listOf(35.0 to 128.0, 36.0 to 129.0), repository.requests)
        assertEquals(catalog.findDistrict("KR-27", "27230"), vm.uiState.selectedLocation)
        vm.selectLocation(junggu)
        vm.suggestRegionForCover(listOf(photo("three", 35.5, 128.5)))
        assertEquals(3, repository.requests.size)
        assertEquals(junggu, vm.uiState.selectedLocation)
    }

    @Test
    fun `사진_위치가_다른_시도면_사용자가_선택한_지역을_덮어쓰지_않는다`() = runSuspend {
        val repository = SuggestionRepository()
        val vm = viewModel(repository, busan)
        vm.suggestRegionForCover(listOf(photo("cover", 35.0, 128.0)))
        assertEquals(busan, vm.uiState.selectedLocation)
        assertNull(vm.uiState.photoRegionAttribution)
    }

    @Test
    fun `사용자가_고른_구군은_같은_시도_추천으로도_덮어쓰지_않는다`() = runSuspend {
        val district = requireNotNull(catalog.findDistrict("KR-27", "27230"))
        val vm = viewModel(SuggestionRepository(), district)
        vm.suggestRegionForCover(listOf(photo("cover", 35.0, 128.0)))
        assertEquals(district, vm.uiState.selectedLocation)
    }

    @Test
    fun `추천_실패는_에러를_표시하지_않고_기존_지역을_유지한다`() = runSuspend {
        val repository = SuggestionRepository { Result.failure(IllegalStateException("503")) }
        val vm = viewModel(repository, daegu)
        vm.suggestRegionForCover(listOf(photo("cover", 35.0, 128.0)))
        assertEquals(daegu, vm.uiState.selectedLocation)
        assertNull(vm.uiState.generalErrorMessage)
        assertNull(vm.uiState.placeSelectionErrorMessage)
        assertFalse(vm.uiState.isSaving)
    }

    @Test
    fun `직접_지역_선택이_필요한_응답은_기존_지역을_유지한다`() = runSuspend {
        val repository = SuggestionRepository {
            Result.success(PhotoRegionSuggestion(null, null, true))
        }
        val vm = viewModel(repository, daegu)
        vm.suggestRegionForCover(listOf(photo("cover", 35.0, 128.0)))
        assertEquals(daegu, vm.uiState.selectedLocation)
    }

    @Test
    fun `제주_시도만_추천되면_자동_선택하지_않는다`() = runSuspend {
        val repository = SuggestionRepository {
            Result.success(PhotoRegionSuggestion("KR", PlaceRegionSuggestion("KR", "49"), false))
        }
        val vm = viewModel(repository, null)
        vm.suggestRegionForCover(listOf(photo("cover", 33.0, 126.0)))
        assertNull(vm.uiState.selectedLocation)
    }

    @Test
    fun `해외_좌표는_선택된_지역이_없으면_국가를_채운다`() = runSuspend {
        val repository = SuggestionRepository {
            Result.success(PhotoRegionSuggestion("JP", PlaceRegionSuggestion("JP"), false))
        }
        val vm = viewModel(repository, null)
        vm.suggestRegionForCover(listOf(photo("cover", 35.6812, 139.7671)))
        assertEquals(catalog.requireByCode("JP"), vm.uiState.selectedLocation)
    }

    @Test
    fun `요청_중_사용자가_바꾼_지역을_늦게_도착한_추천이_덮어쓰지_않는다`() {
        lateinit var pending: Continuation<Result<PhotoRegionSuggestion>>
        val repository = SuggestionRepository { suspendCoroutine { pending = it } }
        val vm = viewModel(repository, daegu)
        var completed = false
        suspend { vm.suggestRegionForCover(listOf(photo("cover", 35.0, 128.0))) }
            .startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) {
                    result.getOrThrow()
                    completed = true
                }
            })
        vm.selectLocation(busan)
        pending.resume(Result.success(daeguSuggestion()))
        assertEquals(true, completed)
        assertEquals(busan, vm.uiState.selectedLocation)
    }

    private fun viewModel(repository: PlaceRepository, location: Location?): TripRecordEditorViewModel {
        val records = FakeTripRecordRepository { "2026-11-09T00:00:00Z" }
        return TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(records),
            updateTripRecord = UpdateTripRecordUseCase(records),
            regionCatalog = catalog,
            placeRepository = repository,
        ).also { it.startCreating(location) }
    }

    private fun photo(id: String, latitude: Double? = null, longitude: Double? = null) = SelectedPhoto(
        id = id,
        displayName = id,
        previewBytes = null,
        latitude = latitude,
        longitude = longitude,
    )

    private class SuggestionRepository(
        private val respond: suspend () -> Result<PhotoRegionSuggestion> = { Result.success(daeguSuggestion()) },
    ) : PlaceRepository {
        val requests = mutableListOf<Pair<Double, Double>>()
        override suspend fun suggestRegion(latitude: Double, longitude: Double): Result<PhotoRegionSuggestion> {
            requests += latitude to longitude
            return respond()
        }
        override suspend fun searchPlaces(query: String, sessionToken: String) = Result.success(emptyList<PlaceCandidate>())
        override suspend fun selectPlace(placeId: String, sessionToken: String) = Result.failure<PlaceSelection>(UnsupportedOperationException())
    }

    companion object {
        private fun daeguSuggestion() = PhotoRegionSuggestion(
            countryCode = "KR",
            suggestedRegion = PlaceRegionSuggestion("KR", "27", "27110"),
            manualRegionRequired = false,
            attribution = "Google Maps",
            attributionUrl = "https://www.google.com/maps",
        )
    }
}
