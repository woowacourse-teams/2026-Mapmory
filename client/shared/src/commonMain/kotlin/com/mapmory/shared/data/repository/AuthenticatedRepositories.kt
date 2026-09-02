package com.mapmory.shared.data.repository

import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripStatistics
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.TagRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.TripStatisticsRepository

internal class AuthenticatedTripRecordRepository(
    private val session: GuestSessionManager,
    private val delegate: TripRecordRepository,
) : TripRecordRepository {
    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> =
        withAuthenticatedSession { delegate.getTripRecords(query) }

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> =
        withAuthenticatedSession { delegate.getTripRecord(id) }

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        withAuthenticatedSession { delegate.createTripRecord(draft) }

    override suspend fun updateTripRecord(
        id: Long,
        draft: TripRecordDraft,
    ): Result<TripRecordData> = withAuthenticatedSession { delegate.updateTripRecord(id, draft) }

    override suspend fun deleteTripRecord(id: Long): Result<Unit> =
        withAuthenticatedSession { delegate.deleteTripRecord(id) }

    private suspend fun <T> withAuthenticatedSession(
        request: suspend () -> Result<T>,
    ): Result<T> = session.ensureAuthenticated().fold(
        onSuccess = { request() },
        onFailure = Result.Companion::failure,
    )
}

internal class AuthenticatedMapSummaryRepository(
    private val session: GuestSessionManager,
    private val delegate: MapSummaryRepository,
) : MapSummaryRepository {
    override suspend fun getRootRegions(tagId: Long?): Result<List<MapRegionSummary>> =
        withAuthenticatedSession { delegate.getRootRegions(tagId) }

    override suspend fun getChildRegions(regionId: Long, tagId: Long?): Result<List<MapRegionSummary>> =
        withAuthenticatedSession { delegate.getChildRegions(regionId, tagId) }

    private suspend fun <T> withAuthenticatedSession(
        request: suspend () -> Result<T>,
    ): Result<T> = session.ensureAuthenticated().fold(
        onSuccess = { request() },
        onFailure = Result.Companion::failure,
    )
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
    ): Result<T> = session.ensureAuthenticated().fold(
        onSuccess = { request() },
        onFailure = Result.Companion::failure,
    )
}

internal class AuthenticatedTripStatisticsRepository(
    private val session: GuestSessionManager,
    private val delegate: TripStatisticsRepository,
) : TripStatisticsRepository {
    override suspend fun getStatistics(): Result<TripStatistics> =
        session.ensureAuthenticated().fold(
            onSuccess = { delegate.getStatistics() },
            onFailure = Result.Companion::failure,
        )
}
