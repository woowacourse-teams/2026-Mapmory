package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.model.MapRegionSummary

interface MapSummaryRepository {
    suspend fun getRootRegions(): Result<List<MapRegionSummary>>

    suspend fun getChildRegions(regionId: Long): Result<List<MapRegionSummary>>
}
