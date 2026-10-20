package com.mapmory.shared.analytics

import com.mapmory.shared.domain.model.*
import com.mapmory.shared.domain.repository.*

/** Preserve progress reporting and cache APIs while observing real repository outcomes. */
internal class AnalyticsTripRecordRepository(
    private val delegate: TripRecordRepository,
    private val analytics: MapmoryAnalytics,
) : TripRecordRepository by delegate, ProgressReportingTripRecordRepository {
    override suspend fun getTripRecords(query: TripRecordQuery) =
        analytics.observeOperation("record_list_load") { delegate.getTripRecords(query) }

    override suspend fun getTripRecord(id: Long) =
        analytics.observeOperation("record_detail_load") { delegate.getTripRecord(id) }

    override suspend fun createTripRecord(draft: TripRecordDraft) = createTripRecord(draft) {}

    override suspend fun createTripRecord(draft: TripRecordDraft, onProgress: (Int) -> Unit) =
        analytics.observeOperation("record_create") {
            (delegate as? ProgressReportingTripRecordRepository)?.createTripRecord(draft, onProgress)
                ?: delegate.createTripRecord(draft)
        }

    // The existing editor emits record_save_completed for edits, with its original parameters.
    override suspend fun updateTripRecord(id: Long, draft: TripRecordDraft) =
        analytics.observeOperation("record_update") { delegate.updateTripRecord(id, draft) }

    override suspend fun deleteTripRecord(id: Long) =
        analytics.observeOperation("record_delete") { delegate.deleteTripRecord(id) }
            .onSuccess {
                analytics.emit(MapmoryAnalyticsEvent.RECORD_DELETED, mapOf("record_id" to id.toString()))
            }
}

internal class AnalyticsMapSummaryRepository(
    private val delegate: MapSummaryRepository,
    private val analytics: MapmoryAnalytics,
) : MapSummaryRepository by delegate {
    override suspend fun getRootRegions(tagId: Long?) =
        analytics.observeOperation("map_root_load") { delegate.getRootRegions(tagId) }
    override suspend fun getChildRegions(regionId: Long, tagId: Long?) =
        analytics.observeOperation("map_region_load") { delegate.getChildRegions(regionId, tagId) }
}

internal class AnalyticsStatisticsRepository(
    private val delegate: TripStatisticsRepository,
    private val analytics: MapmoryAnalytics,
) : TripStatisticsRepository by delegate {
    override suspend fun getStatistics() =
        analytics.observeOperation("statistics_load") { delegate.getStatistics() }
}

internal class AnalyticsTagRepository(
    private val delegate: TagRepository,
    private val analytics: MapmoryAnalytics,
) : TagRepository {
    override suspend fun getTags() = analytics.observeOperation("tags_load") { delegate.getTags() }
    override suspend fun createTag(name: String) =
        analytics.observeOperation("tag_create") { delegate.createTag(name) }
            .onSuccess { analytics.emit(MapmoryAnalyticsEvent.TAG_CREATED) }
}

internal class AnalyticsPlaceRepository(
    private val delegate: PlaceRepository,
    private val analytics: MapmoryAnalytics,
) : PlaceRepository {
    override suspend fun searchPlaces(query: String, sessionToken: String) =
        analytics.observeOperation("place_search") { delegate.searchPlaces(query, sessionToken) }
    override suspend fun selectPlace(placeId: String, sessionToken: String) =
        analytics.observeOperation("place_select") { delegate.selectPlace(placeId, sessionToken) }
}

internal class AnalyticsFeedbackRepository(
    private val delegate: ServiceFeedbackRepository,
    private val analytics: MapmoryAnalytics,
) : ServiceFeedbackRepository {
    override suspend fun submit(category: ServiceFeedbackCategory, content: String, platform: ServiceFeedbackPlatform) =
        analytics.observeOperation("feedback_submit") { delegate.submit(category, content, platform) }
            .onSuccess {
                analytics.emit(MapmoryAnalyticsEvent.FEEDBACK_SUBMITTED, mapOf("category" to category.name.lowercase()))
            }
}
