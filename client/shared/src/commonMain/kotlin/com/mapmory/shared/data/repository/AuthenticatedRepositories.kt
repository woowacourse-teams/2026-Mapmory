package com.mapmory.shared.data.repository

import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.PlaceRepository
import com.mapmory.shared.domain.repository.TagRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.ProgressReportingTripRecordRepository
import com.mapmory.shared.domain.repository.TripStatisticsRepository
import com.mapmory.shared.domain.model.ServiceFeedbackCategory
import com.mapmory.shared.domain.model.ServiceFeedbackPlatform
import com.mapmory.shared.domain.repository.ServiceFeedbackRepository
import com.mapmory.shared.domain.model.ShareVideoDownload
import com.mapmory.shared.domain.model.ShareVideoJob
import com.mapmory.shared.domain.repository.ShareVideoRepository

internal class AuthenticatedTripRecordRepository(
    private val session: GuestSessionManager,
    private val delegate: TripRecordRepository,
) : ProgressReportingTripRecordRepository {
    override suspend fun getCachedTripRecords(query: TripRecordQuery): TripRecordPage? =
        session.withAuthenticatedSession { Result.success(delegate.getCachedTripRecords(query)) }.getOrNull()

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> =
        withAuthenticatedSession { delegate.getTripRecords(query) }

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> =
        withAuthenticatedSession { delegate.getTripRecord(id) }

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        withAuthenticatedSession { delegate.createTripRecord(draft) }

    override suspend fun createTripRecord(
        draft: TripRecordDraft,
        onProgress: (Int) -> Unit,
    ): Result<TripRecordData> = withAuthenticatedSession {
        val reporting = delegate as? ProgressReportingTripRecordRepository
        reporting?.createTripRecord(draft, onProgress) ?: delegate.createTripRecord(draft)
    }

    override suspend fun updateTripRecord(
        id: Long,
        draft: TripRecordDraft,
    ): Result<TripRecordData> = withAuthenticatedSession { delegate.updateTripRecord(id, draft) }

    override suspend fun deleteTripRecord(id: Long): Result<Unit> =
        withAuthenticatedSession { delegate.deleteTripRecord(id) }

    private suspend fun <T> withAuthenticatedSession(
        request: suspend () -> Result<T>,
    ): Result<T> = session.withAuthenticatedSession(request)
}

internal class AuthenticatedMapSummaryRepository(
    private val session: GuestSessionManager,
    private val delegate: MapSummaryRepository,
) : MapSummaryRepository {
    override fun getCachedRootRegions(tagId: Long?) = if (session.state.value.pendingLogin || session.state.value.pendingLogout) null else delegate.getCachedRootRegions(tagId)
    override fun getCachedChildRegions(regionId: Long, tagId: Long?) = if (session.state.value.pendingLogin || session.state.value.pendingLogout) null else delegate.getCachedChildRegions(regionId, tagId)
    override suspend fun getRootRegions(tagId: Long?): Result<List<MapRegionSummary>> =
        withAuthenticatedSession { delegate.getRootRegions(tagId) }

    override suspend fun getChildRegions(regionId: Long, tagId: Long?): Result<List<MapRegionSummary>> =
        withAuthenticatedSession { delegate.getChildRegions(regionId, tagId) }

    private suspend fun <T> withAuthenticatedSession(
        request: suspend () -> Result<T>,
    ): Result<T> = session.withAuthenticatedSession(request)
}

internal class AuthenticatedTagRepository(
    private val session: GuestSessionManager,
    private val delegate: TagRepository,
) : TagRepository {
    override suspend fun getTags(): Result<List<Tag>> =
        withAuthenticatedSession(delegate::getTags)

    override suspend fun createTag(name: String): Result<Tag> =
        withAuthenticatedSession { delegate.createTag(name) }

    private suspend fun <T> withAuthenticatedSession(
        request: suspend () -> Result<T>,
    ): Result<T> = session.withAuthenticatedSession(request)
}

internal class AuthenticatedTripStatisticsRepository(
    private val session: GuestSessionManager,
    private val delegate: TripStatisticsRepository,
) : TripStatisticsRepository {
    override fun getCachedStatistics() = if (session.state.value.pendingLogin || session.state.value.pendingLogout) null else delegate.getCachedStatistics()
    override suspend fun getStatistics(): Result<TripStatistics> =
        session.withAuthenticatedSession { delegate.getStatistics() }
}

internal class AuthenticatedServiceFeedbackRepository(
    private val session: GuestSessionManager,
    private val delegate: ServiceFeedbackRepository,
) : ServiceFeedbackRepository {
    override suspend fun submit(
        category: ServiceFeedbackCategory,
        content: String,
        platform: ServiceFeedbackPlatform,
    ): Result<Unit> = session.withAuthenticatedSession { delegate.submit(category, content, platform) }
}

internal class AuthenticatedShareVideoRepository(
    private val session: GuestSessionManager,
    private val delegate: ShareVideoRepository,
) : ShareVideoRepository {
    override suspend fun createTravelTimeline(): Result<ShareVideoJob> =
        withAuthenticatedSession(delegate::createTravelTimeline)

    override suspend fun getJob(jobId: Long): Result<ShareVideoJob> =
        withAuthenticatedSession { delegate.getJob(jobId) }

    override suspend fun getDownloadUrl(jobId: Long): Result<ShareVideoDownload> =
        withAuthenticatedSession { delegate.getDownloadUrl(jobId) }

    private suspend fun <T> withAuthenticatedSession(
        request: suspend () -> Result<T>,
    ): Result<T> = session.withAuthenticatedSession(request)
}

internal class AuthenticatedPlaceRepository(
    private val session: GuestSessionManager,
    private val delegate: PlaceRepository,
) : PlaceRepository {
    override suspend fun searchPlaces(query: String, sessionToken: String): Result<List<PlaceCandidate>> =
        session.withAuthenticatedSession { delegate.searchPlaces(query, sessionToken) }

    override suspend fun selectPlace(placeId: String, sessionToken: String): Result<PlaceSelection> =
        session.withAuthenticatedSession { delegate.selectPlace(placeId, sessionToken) }
}
