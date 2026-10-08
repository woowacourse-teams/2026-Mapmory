package com.mapmory.shared.data.repository

import android.content.Context
import com.mapmory.shared.data.auth.authTokenStorageKey
import com.mapmory.shared.data.local.triprecord.TripRecordListDatabase
import com.mapmory.shared.data.local.triprecord.TripRecordPageEntity
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery

class AndroidTripRecordListCache(context: Context, apiBaseUrl: String) : TripRecordListCache {
    private val dao = TripRecordListDatabase.getInstance(context, authTokenStorageKey(apiBaseUrl)).pages()

    override suspend fun read(query: TripRecordQuery): TripRecordPage? =
        dao.read(query.cacheKey())?.let(TripRecordListCacheCodec::decode)

    override suspend fun write(query: TripRecordQuery, page: TripRecordPage) {
        dao.write(TripRecordPageEntity(query.cacheKey(), TripRecordListCacheCodec.encode(page)))
    }

    override suspend fun clear() = dao.clear()
}

private fun TripRecordQuery.cacheKey() = "$locationId:$tagId:$page:$size"
