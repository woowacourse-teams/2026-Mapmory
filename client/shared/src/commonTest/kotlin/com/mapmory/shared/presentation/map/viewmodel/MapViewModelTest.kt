package com.mapmory.shared.presentation.map.viewmodel

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapViewModelTest {
    @Test
    fun `새로고침은_기록_지역_변경_후_열린_시도를_다시_조회한다`() = runSuspend {
        val catalog = StaticRegionCatalog()
        val repository = FakeTripRecordRepository(
            regionCatalog = catalog,
            now = { "2026-08-24T00:00:00" },
        )
        val gangnam = catalog.requireByCode("11680")
        val seocho = catalog.requireByCode("11650")
        val record = repository.createTripRecord(draft(gangnam.id, "서울 여행")).getOrThrow()
        val viewModel = MapViewModel(repository, catalog)
        viewModel.refresh()
        viewModel.openProvince("KR-11")

        repository.updateTripRecord(record.id, draft(seocho.id, "서초 여행")).getOrThrow()
        viewModel.refresh()

        assertEquals(setOf("11650"), viewModel.visitedDistrictCodes("KR-11"))
    }

    @Test
    fun `새로고침은_저장소_기록에서_방문_국가_시도_지역을_구성한다`() = runSuspend {
        val catalog = StaticRegionCatalog()
        val repository = FakeTripRecordRepository(
            regionCatalog = catalog,
            now = { "2026-08-24T00:00:00" },
        )
        val gangnam = catalog.requireByCode("11680")
        val seoul = catalog.requireByCode("KR-11")
        val japan = catalog.requireByCode("JP")
        repository.createTripRecord(draft(gangnam.id, "서울 여행")).getOrThrow()
        repository.createTripRecord(draft(japan.id, "일본 여행")).getOrThrow()
        val korea = repository.getRootRegions().getOrThrow().single { it.code == "KR" }
        val serverSeoul = repository.getChildRegions(korea.regionId).getOrThrow().single { it.code == "11" }
        assertEquals(
            setOf("11680"),
            repository.getChildRegions(serverSeoul.regionId).getOrThrow().map { it.code }.toSet(),
        )
        val viewModel = MapViewModel(repository, catalog)

        viewModel.refresh()
        viewModel.openProvince("KR-11")

        assertEquals(setOf("KR", "JP"), viewModel.visitedCountryCodes)
        assertEquals(setOf("KR-11"), viewModel.visitedProvinceCodes)
        assertEquals(setOf("11680"), viewModel.visitedDistrictCodes("KR-11"))
        assertTrue(viewModel.hasRecords(gangnam))
        assertTrue(viewModel.hasRecords(seoul))
    }

    private fun draft(locationId: Long, title: String) = TripRecordDraft(
        locationId = locationId,
        title = title,
        content = "",
        startDate = "2026-08-01",
        endDate = null,
        mediaObjectKeys = emptyList(),
    )
}
