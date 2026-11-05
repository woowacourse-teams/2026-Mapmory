package com.mapmory.shared.data.local.triprecord

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmory.shared.data.repository.AndroidTripRecordListCache
import com.mapmory.shared.data.repository.TripRecordListCacheCodec
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSort
import com.mapmory.shared.domain.model.TripRecordSummary
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripRecordListDatabaseTest {
    @Test
    fun `손상된_캐시만_삭제하고_다음_서버_응답을_정상적으로_저장한다`() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TripRecordListDatabase::class.java).build()
        try {
            val dao = database.pages()
            val cache = AndroidTripRecordListCache(dao)
            val query = TripRecordQuery()
            val otherQuery = query.copy(page = 1)
            val page = TripRecordPage(emptyList(), 0, 20, 0, 0)
            cache.write(otherQuery, page)
            for (payload in listOf("{broken-json", "{}")) {
                dao.write(TripRecordPageEntity("null:null:0:20:CREATED_AT_DESC", payload))
                assertNull(cache.read(query))
                assertNull(dao.read("null:null:0:20:CREATED_AT_DESC"))
                assertNull(cache.read(query))
                assertEquals(page, cache.read(otherQuery))
            }
            cache.write(query, page)
            assertEquals(page, cache.read(query))
        } finally {
            database.close()
        }
    }

    @Test
    fun `디코딩_중_정상_값으로_교체된_행은_삭제하지_않는다`() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TripRecordListDatabase::class.java).build()
        try {
            val dao = database.pages()
            val query = TripRecordQuery()
            val page = TripRecordPage(emptyList(), 0, 20, 0, 0)
            dao.write(TripRecordPageEntity("null:null:0:20:CREATED_AT_DESC", "broken"))
            val racingDao = object : TripRecordPageDao by dao {
                override suspend fun read(queryKey: String): String? {
                    val previous = dao.read(queryKey)
                    dao.write(TripRecordPageEntity(queryKey, TripRecordListCacheCodec.encode(page)))
                    return previous
                }
            }
            assertNull(AndroidTripRecordListCache(racingDao).read(query))
            assertEquals(page, AndroidTripRecordListCache(dao).read(query))
        } finally {
            database.close()
        }
    }

    @Test
    fun `DB를_다시_열어도_목록이_남고_조회조건별로_분리된다`() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "journal-cache-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, TripRecordListDatabase::class.java, name).build()
        var database = open()
        val page = TripRecordPage(
            records = listOf(TripRecordSummary(1, "서울 여행", "서울", "2026-10-08", null)),
            page = 0, size = 20, totalElements = 1, totalPages = 1,
        )
        val query = TripRecordQuery()
        val dateSortedQuery = query.copy(sort = TripRecordSort.TRAVEL_DATE_DESC)
        val key = "null:null:0:20:CREATED_AT_DESC"
        val dateSortedKey = "null:null:0:20:TRAVEL_DATE_DESC"
        val dateSortedPage = page.copy(
            records = listOf(TripRecordSummary(2, "과거 여행", "서울", "2026-10-07", null)),
        )
        try {
            AndroidTripRecordListCache(database.pages()).write(query, page)
            AndroidTripRecordListCache(database.pages()).write(dateSortedQuery, dateSortedPage)
            database.close()
            database = open()
            assertEquals(page, TripRecordListCacheCodec.decode(requireNotNull(database.pages().read(key))))
            assertEquals(dateSortedPage, TripRecordListCacheCodec.decode(requireNotNull(database.pages().read(dateSortedKey))))
            assertNull(database.pages().read("101:null:0:20:CREATED_AT_DESC"))
            val reopenedCache = AndroidTripRecordListCache(database.pages())
            assertEquals(page, reopenedCache.read(query))
            assertEquals(dateSortedPage, reopenedCache.read(dateSortedQuery))
            val empty = page.copy(records = emptyList(), totalElements = 0, totalPages = 0)
            reopenedCache.write(query, empty)
            assertEquals(empty, TripRecordListCacheCodec.decode(requireNotNull(database.pages().read(key))))
            database.pages().clear()
            assertNull(database.pages().read(key))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
