package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.MapRegionLevel
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.MapRegionType
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals

class CachedMapSummaryRepositoryTest {
    @Test
    fun `지도_요약을_캐시해_새_화면에서도_즉시_복원하고_무효화한다`() = runSuspend {
        val korea = summary(1, "KR", MapRegionType.COUNTRY)
        val seoul = summary(11, "11", MapRegionType.PROVINCE).copy(parentRegionId = korea.regionId)
        val gangnam = summary(111, "11680", MapRegionType.DISTRICT).copy(parentRegionId = seoul.regionId)
        val cache = MemoryMapSummaryCache()
        val repository = CachedMapSummaryRepository(
            delegate = StaticMapSummaryRepository(
                roots = listOf(korea),
                children = mapOf(korea.regionId to listOf(seoul), seoul.regionId to listOf(gangnam)),
                allRegions = listOf(korea, seoul, gangnam),
            ),
            cache = cache,
        )

        repository.getMapSummary().getOrThrow()

        val restored = CachedMapSummaryRepository(
            delegate = StaticMapSummaryRepository(emptyList(), emptyMap()),
            cache = cache,
        )
        assertEquals(listOf(korea), restored.getCachedRootRegions())
        assertEquals(listOf(seoul), restored.getCachedChildRegions(korea.regionId))
        assertEquals(listOf(gangnam), restored.getCachedChildRegions(seoul.regionId))

        restored.invalidate()

        assertEquals(null, restored.getCachedRootRegions())
        assertEquals(null, restored.getCachedChildRegions(korea.regionId))
    }
}

private class StaticMapSummaryRepository(
    private val roots: List<MapRegionSummary>,
    private val children: Map<Long, List<MapRegionSummary>>,
    private val allRegions: List<MapRegionSummary> = emptyList(),
) : MapSummaryRepository {
    override suspend fun getRootRegions(tagId: Long?): Result<List<MapRegionSummary>> = Result.success(roots)

    override suspend fun getChildRegions(regionId: Long, tagId: Long?): Result<List<MapRegionSummary>> =
        Result.success(children[regionId].orEmpty())

    override suspend fun getMapSummary(tagId: Long?): Result<List<MapRegionSummary>> = Result.success(allRegions)
}

private fun summary(id: Long, code: String, type: MapRegionType) = MapRegionSummary(
    regionId = id,
    code = code,
    type = type,
    name = code,
    count = 1,
    level = MapRegionLevel.LOW,
)
