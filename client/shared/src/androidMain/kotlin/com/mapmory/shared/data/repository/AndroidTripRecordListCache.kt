package com.mapmory.shared.data.repository

import android.content.Context
import com.mapmory.shared.data.auth.authTokenStorageKey
import com.mapmory.shared.data.local.triprecord.TripRecordListDatabase
import com.mapmory.shared.data.local.triprecord.TripRecordPageDao
import com.mapmory.shared.data.local.triprecord.TripRecordPageEntity
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery

class AndroidTripRecordListCache internal constructor(private val dao: TripRecordPageDao) : TripRecordListCache {
    constructor(context: Context, apiBaseUrl: String) : this(
        TripRecordListDatabase.getInstance(context, authTokenStorageKey(apiBaseUrl)).pages(),
    )

    override suspend fun read(query: TripRecordQuery): TripRecordPage? {
        val key = query.cacheKey()
        val payload = dao.read(key) ?: return null
        val page = TripRecordListCacheCodec.decode(payload)
        if (page == null) {
            // 읽은 뒤 정상 응답으로 교체된 행은 삭제하지 않는다.
            dao.deleteIfMatches(key, payload)
        }
        return page
    }

    override suspend fun write(query: TripRecordQuery, page: TripRecordPage) {
        dao.write(TripRecordPageEntity(query.cacheKey(), TripRecordListCacheCodec.encode(page)))
    }

    override suspend fun clear() = dao.clear()
}

private fun TripRecordQuery.cacheKey() = "$locationId:$tagId:$page:$size"
