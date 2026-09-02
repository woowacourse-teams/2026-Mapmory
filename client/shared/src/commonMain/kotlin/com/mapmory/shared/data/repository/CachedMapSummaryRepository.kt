package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.repository.MapSummaryRepository
import kotlinx.serialization.Serializable

@Serializable
data class MapSummarySnapshot(
    val roots: List<MapRegionSummary> = emptyList(),
    val childrenByRegionId: Map<Long, List<MapRegionSummary>> = emptyMap(),
)

interface MapSummaryCache {
    fun read(): MapSummarySnapshot?

    fun write(snapshot: MapSummarySnapshot)

    fun clear()
}

class MemoryMapSummaryCache : MapSummaryCache {
    private var snapshot: MapSummarySnapshot? = null

    override fun read(): MapSummarySnapshot? = snapshot

    override fun write(snapshot: MapSummarySnapshot) {
        this.snapshot = snapshot
    }

    override fun clear() {
        snapshot = null
    }
}

internal class CachedMapSummaryRepository(
    private val delegate: MapSummaryRepository,
    private val cache: MapSummaryCache,
) : MapSummaryRepository {
    private var snapshot = cache.read() ?: MapSummarySnapshot()

    override fun getCachedRootRegions(tagId: Long?): List<MapRegionSummary>? =
        if (tagId == null) snapshot.roots.takeIf { roots -> roots.isNotEmpty() } else null

    override fun getCachedChildRegions(regionId: Long, tagId: Long?): List<MapRegionSummary>? =
        if (tagId == null) snapshot.childrenByRegionId[regionId] else null

    override suspend fun getRootRegions(tagId: Long?): Result<List<MapRegionSummary>> =
        delegate.getRootRegions(tagId).onSuccess { roots ->
            if (tagId == null) {
                snapshot = snapshot.copy(roots = roots)
                runCatching { cache.write(snapshot) }
            }
        }

    override suspend fun getChildRegions(regionId: Long, tagId: Long?): Result<List<MapRegionSummary>> =
        delegate.getChildRegions(regionId, tagId).onSuccess { children ->
            if (tagId == null) {
                snapshot = snapshot.copy(
                    childrenByRegionId = snapshot.childrenByRegionId + (regionId to children),
                )
                runCatching { cache.write(snapshot) }
            }
        }

    fun invalidate() {
        snapshot = MapSummarySnapshot()
        runCatching(cache::clear)
    }
}
