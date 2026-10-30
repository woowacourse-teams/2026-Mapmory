package com.mapmory.shared.presentation.map.viewmodel

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.MapRegionLevel
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.MapRegionType
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapViewModelTest {
    @Test
    fun `화면을_다시_그리거나_새로고침해도_확인한_현재위치를_유지한다`() = runSuspend {
        val repository = object : MapSummaryRepository {
            override suspend fun getRootRegions(tagId: Long?) = Result.success(emptyList<MapRegionSummary>())
            override suspend fun getChildRegions(regionId: Long, tagId: Long?) =
                Result.success(emptyList<MapRegionSummary>())
        }
        val viewModel = MapViewModel(repository, StaticRegionCatalog())
        val point = com.mapmory.shared.presentation.map.domain.GeoPoint(127f, 37f)
        viewModel.updateCurrentLocation(point)
        viewModel.refreshIfNeeded(0)
        assertEquals(point, viewModel.currentLocation)
        viewModel.refreshIfNeeded(1)
        assertEquals(point, viewModel.currentLocation)
    }

    @Test
    fun `지도_재진입은_열린_지역을_유지하고_같은_데이터를_재조회하지_않는다`() = runSuspend {
        var requests = 0
        val repository = object : MapSummaryRepository {
            override suspend fun getRootRegions(tagId: Long?): Result<List<MapRegionSummary>> {
                requests++
                return Result.success(emptyList())
            }
            override suspend fun getChildRegions(regionId: Long, tagId: Long?) =
                Result.success(emptyList<MapRegionSummary>())
        }
        val viewModel = MapViewModel(repository, StaticRegionCatalog())
        viewModel.refreshIfNeeded(0)
        viewModel.openProvince("KR-11")
        val visible = viewModel.uiState
        viewModel.refreshIfNeeded(0)
        assertEquals(visible, viewModel.uiState)
        assertEquals(1, requests)
        viewModel.refreshIfNeeded(1)
        assertEquals(2, requests)
    }

    @Test
    fun `캐시된_지도_요약으로_첫_화면을_즉시_구성한다`() {
        val korea = MapRegionSummary(1, "KR", MapRegionType.COUNTRY, "대한민국", 2, MapRegionLevel.LOW)
        val seoul = MapRegionSummary(11, "11", MapRegionType.PROVINCE, "서울특별시", 2, MapRegionLevel.LOW)
        val repository = object : MapSummaryRepository {
            override fun getCachedRootRegions(tagId: Long?) = listOf(korea)
            override fun getCachedChildRegions(regionId: Long, tagId: Long?) = listOf(seoul)
            override suspend fun getRootRegions(tagId: Long?) = Result.success(listOf(korea))
            override suspend fun getChildRegions(regionId: Long, tagId: Long?) = Result.success(listOf(seoul))
        }

        val viewModel = MapViewModel(repository, StaticRegionCatalog())

        assertEquals(setOf("KR"), viewModel.visitedCountryCodes)
        assertEquals(setOf("KR-11"), viewModel.visitedProvinceCodes)
    }

    @Test
    fun `선택한_태그가_연결된_지역만_지도에_표시한다`() = runSuspend {
        val catalog = StaticRegionCatalog()
        val repository = FakeTripRecordRepository(catalog) { "2026-08-31T00:00:00" }
        val family = repository.createTag("가족").getOrThrow()
        val solo = repository.createTag("혼자").getOrThrow()
        repository.createTripRecord(draft(catalog.requireByCode("11680").id, "서울").copy(tagIds = listOf(family.id)))
        repository.createTripRecord(draft(catalog.requireByCode("JP").id, "일본").copy(tagIds = listOf(solo.id)))
        val viewModel = MapViewModel(repository, catalog, GetTagsUseCase(repository))

        viewModel.refresh()
        viewModel.selectTag(family.id)

        assertEquals(setOf("KR"), viewModel.visitedCountryCodes)
        assertEquals(family.id, viewModel.uiState.selectedTagId)
    }

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
        assertTrue(viewModel.hasRecords(catalog.requireByCode("KR")))
        assertTrue(viewModel.hasRecords(gangnam))
        assertTrue(viewModel.hasRecords(seoul))
    }

    @Test
    fun `전국지도는_기록한_시군구만_방문으로_표시한다`() = runSuspend {
        val catalog = StaticRegionCatalog()
        val repository = FakeTripRecordRepository(catalog) { "2026-08-31T00:00:00" }
        repository.createTripRecord(draft(catalog.requireByCode("41150").id, "의정부 여행"))
        repository.createTripRecord(draft(catalog.requireByCode("11680").id, "강남 여행"))
        val viewModel = MapViewModel(repository, catalog)

        viewModel.refresh()

        assertEquals(setOf("41150", "11680"), viewModel.visitedKoreaDistrictCodes)
        assertEquals(230, viewModel.koreaDistrictCount)
    }

    @Test
    fun `시도에_직접_저장한_기록은_방문하지_않은_시군구를_칠하지_않는다`() = runSuspend {
        val catalog = StaticRegionCatalog()
        val repository = FakeTripRecordRepository(catalog) { "2026-08-31T00:00:00" }
        val seoul = catalog.requireByCode("KR-11")
        repository.createTripRecord(draft(seoul.id, "서울 기록"))
        repository.createTripRecord(draft(catalog.requireByCode("11680").id, "강남 기록"))
        val viewModel = MapViewModel(repository, catalog)

        viewModel.refresh()

        assertEquals(setOf("11680"), viewModel.visitedKoreaDistrictCodes)
        assertEquals(setOf("KR-11"), viewModel.directlyVisitedProvinceCodes)
        assertEquals(setOf("KR-11", "11680"), viewModel.visitedKoreaMapRegionCodes)
        assertTrue(viewModel.hasRecords(seoul))
    }

    @Test
    fun `부산에_직접_저장한_기록은_부산_전체를_방문_지역으로_표시한다`() = runSuspend {
        val catalog = StaticRegionCatalog()
        val repository = FakeTripRecordRepository(catalog) { "2026-08-31T00:00:00" }
        repository.createTripRecord(draft(catalog.requireByCode("KR-26").id, "부산 기록"))
        val viewModel = MapViewModel(repository, catalog)

        viewModel.refresh()

        assertEquals(setOf("KR-26"), viewModel.directlyVisitedProvinceCodes)
        assertEquals(setOf("KR-26"), viewModel.visitedKoreaMapRegionCodes)
        assertTrue(viewModel.visitedKoreaDistrictCodes.isEmpty())
    }

    @Test
    fun `기록_수가_0인_시군구는_기록이_없는_지역으로_판단한다`() = runSuspend {
        val catalog = StaticRegionCatalog()
        val korea = MapRegionSummary(1, "KR", MapRegionType.COUNTRY, "대한민국", 1, MapRegionLevel.LOW)
        val seoul = MapRegionSummary(11, "11", MapRegionType.PROVINCE, "서울", 1, MapRegionLevel.LOW)
        val empty = MapRegionSummary(11680, "11680", MapRegionType.DISTRICT, "강남구", 0, MapRegionLevel.NONE)
        val visited = MapRegionSummary(11650, "11650", MapRegionType.DISTRICT, "서초구", 1, MapRegionLevel.LOW)
        val repository = object : MapSummaryRepository {
            override suspend fun getRootRegions(tagId: Long?) = Result.success(listOf(korea))
            override suspend fun getChildRegions(regionId: Long, tagId: Long?) = Result.success(
                when (regionId) { 1L -> listOf(seoul); 11L -> listOf(empty, visited); else -> emptyList() },
            )
        }
        val viewModel = MapViewModel(repository, catalog)
        viewModel.refresh()

        assertFalse(viewModel.hasRecords(catalog.requireByCode("11680")))
        assertTrue(viewModel.hasRecords(catalog.requireByCode("11650")))
        assertEquals(setOf("11650"), viewModel.visitedDistrictCodes("KR-11"))
    }

    private fun draft(locationId: Long, title: String) = TripRecordDraft(
        locationId = locationId,
        title = title,
        content = "",
        startDate = "2026-08-01",
        endDate = null,
        mediaObjectKeys = listOf("records/$locationId/photo.jpg"),
    )
}
