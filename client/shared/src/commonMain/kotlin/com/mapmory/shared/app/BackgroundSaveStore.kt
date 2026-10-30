package com.mapmory.shared.app

import com.mapmory.shared.domain.model.TripRecordDraft
import kotlinx.serialization.Serializable

@Serializable
data class PendingTripRecordSave(val id: Long, val locationName: String, val draft: TripRecordDraft, val serverSaveStarted: Boolean = false)

/** Writes must be atomic and durable before enqueue returns. Namespace stores by API environment. */
interface BackgroundSaveStore {
    fun read(): List<PendingTripRecordSave>
    fun write(saves: List<PendingTripRecordSave>)
}

object EmptyBackgroundSaveStore : BackgroundSaveStore {
    override fun read(): List<PendingTripRecordSave> = emptyList()
    override fun write(saves: List<PendingTripRecordSave>) = Unit
}

internal expect fun backgroundSaveDispatcher(): kotlinx.coroutines.CoroutineDispatcher
