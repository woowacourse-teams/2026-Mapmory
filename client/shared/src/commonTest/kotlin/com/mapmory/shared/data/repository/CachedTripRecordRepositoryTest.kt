package com.mapmory.shared.data.repository

import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.repository.TripRecordRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachedTripRecordRepositoryTest {
    private val query = TripRecordQuery()
    private val page = TripRecordPage(
        records = listOf(TripRecordSummary(1, "서울 여행", "서울", "2026-10-08", null,
            thumbnailUrl = "https://example.com/signed-photo", thumbnailPreviewBytes = byteArrayOf(1))),
        page = 0, size = 20, totalElements = 1, totalPages = 1,
    )

    @Test
    fun `캐시에는_사진_없이_기록을_저장하고_조건별로_분리한다`() = runBlocking {
        val repository = CachedTripRecordRepository(source(), MemoryTripRecordListCache())
        repository.getTripRecords(query)
        val cached = requireNotNull(repository.getCachedTripRecords(query))
        assertEquals("서울 여행", cached.records.single().title)
        assertNull(cached.records.single().thumbnailUrl)
        assertNull(cached.records.single().thumbnailPreviewBytes)
        assertNull(repository.getCachedTripRecords(query.copy(page = 1)))
        assertNull(repository.getCachedTripRecords(query.copy(size = 10)))
        assertNull(repository.getCachedTripRecords(query.copy(tagId = 1)))
        assertNull(repository.getCachedTripRecords(query.copy(locationId = 101)))
    }

    @Test
    fun `새_세션에서는_이전_캐시를_버리고_서버_결과를_저장한다`() = runBlocking {
        val cache = MemoryTripRecordListCache()
        cache.write(query, page)
        val repository = CachedTripRecordRepository(source(), cache, discardExistingCache = true)
        assertNull(repository.getCachedTripRecords(query))
        repository.getTripRecords(query)
        assertEquals(1, repository.getCachedTripRecords(query)?.records?.size)
    }

    @Test
    fun `변경_성공은_모든_캐시를_지우고_진행_중이던_조회도_캐시를_되살리지_않는다`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val cache = MemoryTripRecordListCache()
        cache.write(query, page)
        val delegate = object : TripRecordRepository by source() {
            override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> {
                gate.await()
                return Result.success(page)
            }
            override suspend fun deleteTripRecord(id: Long) = Result.success(Unit)
        }
        val repository = CachedTripRecordRepository(delegate, cache)
        val job = launch { repository.getTripRecords(query) }
        yield()
        repository.deleteTripRecord(1)
        gate.complete(Unit)
        job.join()
        assertNull(repository.getCachedTripRecords(query))
    }

    @Test
    fun `생성과_수정_성공도_캐시를_무효화한다`() = runBlocking {
        val cache = MemoryTripRecordListCache()
        val repository = CachedTripRecordRepository(FakeTripRecordRepository { "2026-10-08" }, cache)
        val draft = TripRecordDraft(101, "2026-10-08", listOf("photo.jpg"))
        cache.write(query, page)
        val created = repository.createTripRecord(draft) {}.getOrThrow()
        assertNull(repository.getCachedTripRecords(query))
        cache.write(query, page)
        repository.updateTripRecord(created.id, draft).getOrThrow()
        assertNull(repository.getCachedTripRecords(query))
    }

    @Test
    fun `캐시_입출력_실패는_서버_조회_성공을_막지_않는다`() = runBlocking {
        val broken = object : TripRecordListCache {
            override suspend fun read(query: TripRecordQuery): TripRecordPage? = error("캐시 오류")
            override suspend fun write(query: TripRecordQuery, page: TripRecordPage) = error("캐시 오류")
            override suspend fun clear() = error("캐시 오류")
        }
        val repository = CachedTripRecordRepository(source(), broken)
        assertNull(repository.getCachedTripRecords(query))
        assertTrue(repository.getTripRecords(query).isSuccess)
    }

    @Test
    fun `목록_직렬화는_사진을_제외하고_내용과_페이지를_복원한다`() {
        val payload = TripRecordListCacheCodec.encode(page)
        val restored = requireNotNull(TripRecordListCacheCodec.decode(payload))
        assertEquals(page.copy(records = page.records.map {
            it.copy(thumbnailUrl = null, thumbnailPreviewBytes = null)
        }), restored)
        assertTrue("signed-photo" !in payload)
        assertNull(TripRecordListCacheCodec.decode("잘못된 캐시"))
    }

    private fun source(): TripRecordRepository = object : TripRecordRepository by FakeTripRecordRepository(now = { "2026-10-08" }) {
        override suspend fun getTripRecords(query: TripRecordQuery) = Result.success(page)
    }
}
