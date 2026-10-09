package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.ProgressReportingTripRecordRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TripRecordListCache {
    suspend fun read(query: TripRecordQuery): TripRecordPage?
    suspend fun write(query: TripRecordQuery, page: TripRecordPage)
    suspend fun clear()
}

class MemoryTripRecordListCache : TripRecordListCache {
    private val pages = mutableMapOf<TripRecordQuery, TripRecordPage>()
    override suspend fun read(query: TripRecordQuery) = pages[query]
    override suspend fun write(query: TripRecordQuery, page: TripRecordPage) {
        pages[query] = page.withoutPhotos()
    }
    override suspend fun clear() = pages.clear()
}

/** 로컬 목록은 인증/네트워크를 기다리지 않고 읽고, 서버 조회 성공 시 교체한다. */
internal class CachedTripRecordRepository(
    private val delegate: TripRecordRepository,
    private val cache: TripRecordListCache,
    discardExistingCache: Boolean = false,
) : TripRecordRepository by delegate, ProgressReportingTripRecordRepository {
    private val mutex = Mutex()
    private var needsReset = discardExistingCache
    private var revision = 0L

    override suspend fun getCachedTripRecords(query: TripRecordQuery): TripRecordPage? =
        cacheOperation { cache.read(query) }

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> {
        val requestedRevision = mutex.withLock { revision }
        return delegate.getTripRecords(query).onSuccess { page ->
            cacheOperation {
                // 저장/수정/삭제 전에 시작한 요청은 무효화된 캐시를 되살리지 않는다.
                if (revision == requestedRevision) cache.write(query, page.withoutPhotos())
            }
        }
    }

    override suspend fun createTripRecord(draft: TripRecordDraft) =
        delegate.createTripRecord(draft).onSuccess { invalidate() }

    override suspend fun createTripRecord(draft: TripRecordDraft, onProgress: (Int) -> Unit) =
        ((delegate as? ProgressReportingTripRecordRepository)?.createTripRecord(draft, onProgress)
            ?: delegate.createTripRecord(draft)).onSuccess { invalidate() }

    override suspend fun updateTripRecord(id: Long, draft: TripRecordDraft) =
        delegate.updateTripRecord(id, draft).onSuccess { invalidate() }

    override suspend fun deleteTripRecord(id: Long) =
        delegate.deleteTripRecord(id).onSuccess { invalidate() }

    private suspend fun invalidate() {
        mutex.withLock {
            revision++
            needsReset = true
        }
        cacheOperation { Unit }
    }

    private suspend fun <T> cacheOperation(block: suspend () -> T): T? = mutex.withLock {
        try {
            if (needsReset) {
                cache.clear()
                needsReset = false
            }
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 캐시 실패가 서버 조회나 저장 성공을 실패로 바꾸지 않게 한다.
            null
        }
    }
}

/** 캐시 화면은 기록 정보와 회색 사진 영역만 표시한다. 사진 바이트/서명 URL은 저장하지 않는다. */
private fun TripRecordPage.withoutPhotos() = copy(
    records = records.map {
        it.copy(thumbnailUrl = null, thumbnailUrlExpiresIn = null, thumbnailPreviewBytes = null, media = emptyList())
    },
)
