package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.MapRegionSummary

interface MapSummaryRepository {
    fun getCachedRootRegions(tagId: Long? = null): List<MapRegionSummary>? = null

    fun getCachedChildRegions(regionId: Long, tagId: Long? = null): List<MapRegionSummary>? = null

    suspend fun getRootRegions(tagId: Long? = null): Result<List<MapRegionSummary>>

    suspend fun getChildRegions(regionId: Long, tagId: Long? = null): Result<List<MapRegionSummary>>
}
