package com.mapmory.shared.app

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.data.media.MemoryPhotoPreviewCache
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMediaDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.ProgressReportingTripRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundTripRecordSaverTest {
    @Test
    fun `폐기한_실패_작업의_원본은_마지막_참조가_사라진_뒤_정리한다`() = runBlocking {
        val released = mutableListOf<String>()
        val saver = BackgroundTripRecordSaver(
            repository = object : EmptyTripRecordRepository() {
                override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
                    Result.failure(IllegalStateException("offline"))
            },
            scope = CoroutineScope(coroutineContext),
            automaticRetryDelayMillis = 0,
            localPhotoDataSource = object : com.mapmory.shared.data.media.LocalPhotoDataSource {
                override suspend fun read(localId: String): ByteArray? = null
                override suspend fun release(localId: String) { released += localId }
            },
        )
        val photo = "mapmory-cache://pending-photo"
        val first = saver.enqueue(draft().copy(mediaObjectKeys = listOf(photo)), "서울")
        val second = saver.enqueue(draft().copy(mediaObjectKeys = listOf(photo)), "서울")
        saver.saves.first { saves -> saves.size == 2 && saves.all { it.status == BackgroundSaveStatus.FAILED } }
        assertTrue(released.isEmpty())
        saver.dismissFailure(first)
        kotlinx.coroutines.yield()
        assertTrue(released.isEmpty())
        saver.dismissFailure(second)
        kotlinx.coroutines.yield()
        assertEquals(listOf(photo), released)
        saver.dismissFailure(second)
        kotlinx.coroutines.yield()
        assertEquals(listOf(photo), released)
    }

    @Test
    fun `영속_대기열을_복구하고_완료하면_디스크에서도_삭제한다`() = runBlocking {
        val store = TestSaveStore(listOf(PendingTripRecordSave(7, "서울", draft())))
        val saver = BackgroundTripRecordSaver(
            repository = object : EmptyTripRecordRepository() {
                override suspend fun createTripRecord(draft: TripRecordDraft) = Result.success(draft.toRecord())
            },
            scope = CoroutineScope(coroutineContext),
            execution = object : BackgroundSaveExecution {
                override val store = store
                override suspend fun <T> run(block: suspend () -> T): T = block()
            },
        )
        saver.saves.first { it.isEmpty() }
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `서버_저장_응답이_불확실한_복구_작업은_자동으로_중복_생성하지_않는다`() = runBlocking {
        val store = TestSaveStore(listOf(PendingTripRecordSave(7, "서울", draft(), serverSaveStarted = true)))
        var called = false
        val saver = BackgroundTripRecordSaver(
            repository = object : EmptyTripRecordRepository() {
                override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> {
                    called = true
                    return Result.success(draft.toRecord())
                }
            },
            scope = CoroutineScope(coroutineContext),
            execution = object : BackgroundSaveExecution {
                override val store = store
                override suspend fun <T> run(block: suspend () -> T): T = block()
            },
        )
        assertEquals(BackgroundSaveStatus.FAILED, saver.saves.value.single().status)
        assertEquals(false, called)
        assertEquals(1, store.read().size)
    }

    @Test
    fun `남은_시간은_관측된_진행률로_추정하고_준비와_완료에는_숨긴다`() {
        assertEquals(30L, estimateUploadRemainingSeconds(10_000, 25))
        assertEquals(null, estimateUploadRemainingSeconds(10_000, 0))
        assertEquals(null, estimateUploadRemainingSeconds(500, 25))
        assertEquals(null, estimateUploadRemainingSeconds(10_000, 95))
    }

    @Test
    fun `실패한_업로드의_사진도_예약하고_취소하면_해제한다`() = runBlocking {
        val saver = BackgroundTripRecordSaver(
            repository = object : EmptyTripRecordRepository() {
                override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
                    Result.failure(IllegalStateException("offline"))
            },
            scope = CoroutineScope(coroutineContext),
            automaticRetryDelayMillis = 0,
        )
        val id = saver.enqueue(draft().copy(localMedia = listOf(
            TripRecordMediaDraft("local", 0, null, localPreviewKey = "local"),
        )), "서울")
        assertEquals(setOf("local"), saver.pendingPhotoIds.value)
        saver.saves.first { it.singleOrNull()?.status == BackgroundSaveStatus.FAILED }
        assertEquals(setOf("local"), saver.pendingPhotoIds.value)
        saver.dismissFailure(id)
        assertTrue(saver.pendingPhotoIds.value.isEmpty())
    }

    @Test
    fun `백그라운드 저장 전에 미리보기는 디스크 경계에 저장하고 요청에서는 제거한다`() = runBlocking {
        val cache = MemoryPhotoPreviewCache()
        var receivedDraft: TripRecordDraft? = null
        val repository = object : EmptyTripRecordRepository() {
            override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> {
                receivedDraft = draft
                return Result.success(draft.toRecord())
            }
        }
        val saver = BackgroundTripRecordSaver(
            repository = repository,
            scope = CoroutineScope(coroutineContext),
            photoPreviewCache = cache,
            automaticRetryDelayMillis = 0,
        )
        val preview = byteArrayOf(0x01, 0x02)
        val draft = draft().copy(
            mediaObjectKeys = listOf("content://photo/1"),
            localMedia = listOf(
                TripRecordMediaDraft(
                    objectKey = "content://photo/1",
                    sortOrder = 0,
                    previewBytes = preview,
                    localPreviewKey = "content://photo/1",
                ),
            ),
        )

        saver.enqueue(draft, locationName = "서울")
        saver.saves.first { saves -> saves.isEmpty() }

        assertEquals(null, receivedDraft?.localMedia?.single()?.previewBytes)
        assertContentEquals(preview, cache.read("content://photo/1"))
    }

    @Test
    fun `업로드_진행률과_낙관적_기록_정보를_노출한다`() = runBlocking {
        val continueSave = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = object : EmptyTripRecordRepository(), ProgressReportingTripRecordRepository {
            override suspend fun createTripRecord(
                draft: TripRecordDraft,
                onProgress: (Int) -> Unit,
            ): Result<TripRecordData> {
                onProgress(100)
                continueSave.await()
                onProgress(100)
                return Result.success(draft.toRecord())
            }
        }
        val saver = BackgroundTripRecordSaver(
            repository = repository,
            scope = CoroutineScope(coroutineContext),
            automaticRetryDelayMillis = 0,
        )

        saver.enqueue(draft(), locationName = "서울")
        val saving = saver.saves.first { saves -> saves.singleOrNull()?.progressPercent == 100 }.single()

        assertEquals("서울", saving.locationName)
        assertEquals("2026-10-02", saving.startDate)
        assertEquals(BackgroundSaveStatus.SAVING, saving.status)

        continueSave.complete(Unit)
        saver.saves.first { saves -> saves.isEmpty() }
        Unit
    }

    @Test
    fun `재시도는_진행률을_초기화하고_실제_진행률을_전달한다`() = runBlocking {
        var attempts = 0
        val continueRetry = kotlinx.coroutines.CompletableDeferred<Unit>()
        val repository = object : EmptyTripRecordRepository(), ProgressReportingTripRecordRepository {
            override suspend fun createTripRecord(
                draft: TripRecordDraft,
                onProgress: (Int) -> Unit,
            ): Result<TripRecordData> {
                attempts += 1
                return if (attempts == 1) {
                    onProgress(40)
                    Result.failure(IllegalStateException("temporary"))
                } else {
                    onProgress(50)
                    continueRetry.await()
                    onProgress(100)
                    Result.success(draft.toRecord())
                }
            }
        }
        val saver = BackgroundTripRecordSaver(
            repository = repository,
            scope = CoroutineScope(coroutineContext),
            automaticRetryDelayMillis = 0,
        )

        saver.enqueue(draft(), locationName = "서울")
        val retrying = saver.saves.first { saves ->
            saves.singleOrNull()?.progressPercent == 50
        }.single()

        assertEquals(50, retrying.progressPercent)
        assertEquals(2, attempts)

        continueRetry.complete(Unit)
        saver.saves.first { saves -> saves.isEmpty() }
        Unit
    }

    @Test
    fun `첫_저장_실패_후_한번_자동_재시도한다`() = runBlocking {
        var attempts = 0
        var changedCount = 0
        val repository = object : EmptyTripRecordRepository() {
            override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> {
                attempts += 1
                return if (attempts == 1) Result.failure(IllegalStateException("일시 오류"))
                else Result.success(draft.toRecord())
            }
        }
        val saver = BackgroundTripRecordSaver(
            repository = repository,
            scope = CoroutineScope(coroutineContext),
            onSaved = { changedCount += 1 },
            automaticRetryDelayMillis = 0,
        )

        saver.enqueue(draft(), locationName = "서울")
        saver.saves.first { saves -> saves.isEmpty() && attempts > 0 }

        assertEquals(2, attempts)
        assertEquals(1, changedCount)
    }

    @Test
    fun `두번_실패하면_알리고_사용자가_다시_시도할_수_있다`() = runBlocking {
        var attempts = 0
        val repository = object : EmptyTripRecordRepository() {
            override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> {
                attempts += 1
                return if (attempts <= 2) Result.failure(IllegalStateException("네트워크 오류"))
                else Result.success(draft.toRecord())
            }
        }
        val saver = BackgroundTripRecordSaver(
            repository = repository,
            scope = CoroutineScope(coroutineContext),
            automaticRetryDelayMillis = 0,
        )

        val id = saver.enqueue(draft(), locationName = "서울")
        val failed = saver.saves.first { saves ->
            saves.singleOrNull()?.status == BackgroundSaveStatus.FAILED
        }.single()

        assertEquals(id, failed.id)
        assertEquals(2, attempts)
        assertEquals("서울", failed.locationName)
        assertTrue(failed.errorMessage?.contains("네트워크 오류") == true)

        saver.retry(id)
        saver.saves.first { saves -> saves.isEmpty() }
        assertEquals(3, attempts)
    }

    @Test
    fun `자동_재시도_후_성공하면_완료_이벤트만_한번_남긴다`() = runBlocking {
        var attempts = 0
        val analytics = RecordingAnalytics()
        val saver = BackgroundTripRecordSaver(
            repository = object : EmptyTripRecordRepository() {
                override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> {
                    attempts += 1
                    return if (attempts == 1) Result.failure(IllegalStateException("일시 오류"))
                    else Result.success(draft.toRecord())
                }
            },
            scope = CoroutineScope(coroutineContext),
            automaticRetryDelayMillis = 0,
            analytics = analytics,
        )

        saver.enqueue(draft().copy(tagIds = listOf(3)), locationName = "서울")
        saver.saves.first { saves -> saves.isEmpty() && attempts > 0 }

        val (name, parameters) = analytics.events.single()
        assertEquals(MapmoryAnalyticsEvent.RECORD_SAVE_COMPLETED, name)
        assertEquals("create", parameters["mode"])
        assertEquals("background", parameters["save_mode"])
        assertEquals("true", parameters["has_title"])
        assertEquals("true", parameters["has_tags"])
        assertEquals("false", parameters["has_photos"])
        assertTrue(parameters["duration_ms"]?.toLongOrNull() != null)
    }

    @Test
    fun `최종_실패는_오류_유형과_함께_한번_남기고_다시_시도한_결과도_남긴다`() = runBlocking {
        var attempts = 0
        val analytics = RecordingAnalytics()
        val saver = BackgroundTripRecordSaver(
            repository = object : EmptyTripRecordRepository() {
                override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> {
                    attempts += 1
                    return if (attempts <= 2) Result.failure(IllegalStateException("네트워크 오류"))
                    else Result.success(draft.toRecord())
                }
            },
            scope = CoroutineScope(coroutineContext),
            automaticRetryDelayMillis = 0,
            analytics = analytics,
        )

        val id = saver.enqueue(draft(), locationName = "서울")
        saver.saves.first { saves -> saves.singleOrNull()?.status == BackgroundSaveStatus.FAILED }

        val (failedName, failedParameters) = analytics.events.single()
        assertEquals(MapmoryAnalyticsEvent.RECORD_SAVE_FAILED, failedName)
        assertEquals("IllegalStateException", failedParameters["error_type"])
        assertEquals("background", failedParameters["save_mode"])

        saver.retry(id)
        saver.saves.first { saves -> saves.isEmpty() }
        assertContentEquals(
            listOf(MapmoryAnalyticsEvent.RECORD_SAVE_FAILED, MapmoryAnalyticsEvent.RECORD_SAVE_COMPLETED),
            analytics.events.map { it.first },
        )
    }
}

private class RecordingAnalytics : MapmoryAnalytics {
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    override fun logEvent(name: String, parameters: Map<String, String>) {
        events += name to parameters
    }

    override fun setUserProperty(name: String, value: String?) = Unit
}

private open class EmptyTripRecordRepository : TripRecordRepository {
    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> =
        Result.failure(UnsupportedOperationException())

    override suspend fun getTripRecord(id: Long): Result<TripRecordData> =
        Result.failure(UnsupportedOperationException())

    override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
        Result.failure(UnsupportedOperationException())

    override suspend fun updateTripRecord(
        id: Long,
        draft: TripRecordDraft,
    ): Result<TripRecordData> = Result.failure(UnsupportedOperationException())

    override suspend fun deleteTripRecord(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}

private fun draft() = TripRecordDraft(
    locationId = 1,
    title = "백그라운드 기록",
    content = null,
    startDate = "2026-10-02",
    endDate = null,
    mediaObjectKeys = emptyList(),
)

private fun TripRecordDraft.toRecord() = TripRecordData(
    id = 101,
    locationId = locationId,
    title = title,
    content = content.orEmpty(),
    startDate = startDate,
    endDate = endDate,
    media = emptyList(),
    createdAt = "2026-10-02T00:00:00",
    updatedAt = "2026-10-02T00:00:00",
)

private class TestSaveStore(private var items: List<PendingTripRecordSave>) : BackgroundSaveStore {
    override fun read() = items
    override fun write(saves: List<PendingTripRecordSave>) { items = saves }
}
