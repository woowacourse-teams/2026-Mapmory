package com.mapmory.shared.data.repository

import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.domain.model.MapRegionSummary
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.MapSummaryRepository
import com.mapmory.shared.domain.repository.TripRecordRepository

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
    override suspend fun getRootRegions(): Result<List<MapRegionSummary>> =
        withAuthenticatedSession(delegate::getRootRegions)

    override suspend fun getChildRegions(regionId: Long): Result<List<MapRegionSummary>> =
        withAuthenticatedSession { delegate.getChildRegions(regionId) }

    private suspend fun <T> withAuthenticatedSession(
        request: suspend () -> Result<T>,
    ): Result<T> = session.ensureAuthenticated().fold(
        onSuccess = { request() },
        onFailure = Result.Companion::failure,
    )
}
