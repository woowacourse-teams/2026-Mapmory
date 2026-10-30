package com.mapmory.shared.analytics

import com.mapmory.shared.app.BackgroundTripRecordSaver
import com.mapmory.shared.app.BackgroundSaveStatus
import com.mapmory.shared.app.createAppContainer
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.repository.ProgressReportingTripRecordRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class AnalyticsRepositoriesTest {
    private fun fake() = FakeTripRecordRepository(now = { "2026-10-19T00:00:00" })
    private fun draft() = TripRecordDraft(
        locationId = 1, title = "private title", content = "private content",
        startDate = "2026-10-18", endDate = null, mediaObjectKeys = listOf("private-photo-key"),
    )

    @Test fun `background retry counts attempts but creation success only once and forwards progress`() = runBlocking {
        val analytics = RecordingAnalytics()
        val backing = fake()
        var attempts = 0
        val progress = mutableListOf<Int>()
        val repository = object : TripRecordRepository by backing, ProgressReportingTripRecordRepository {
            override suspend fun createTripRecord(draft: TripRecordDraft, onProgress: (Int) -> Unit) =
                if (++attempts == 1) Result.failure(IllegalStateException("failed")) else {
                    onProgress(50)
                    progress += 50
                    backing.createTripRecord(draft)
                }
        }
        val saver = BackgroundTripRecordSaver(
            analytics = analytics,
            repository = AnalyticsTripRecordRepository(repository, analytics),
            scope = CoroutineScope(coroutineContext), automaticRetryDelayMillis = 0,
        )
        saver.enqueue(draft(), "private location")
        assertTrue(analytics.events.isEmpty()) // Enqueue is not save completion.
        withTimeout(5_000) { saver.saves.first { it.isEmpty() } }
        assertEquals(2, attempts)
        assertEquals(listOf(50), progress)
        assertFalse(analytics.events.any { it.first == MapmoryAnalyticsEvent.RECORD_SAVE_FAILED })
        assertEquals(2, analytics.events.count { it.first == MapmoryAnalyticsEvent.OPERATION_COMPLETED })
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        val completed = analytics.events.single { it.first == MapmoryAnalyticsEvent.RECORD_SAVE_COMPLETED }.second
        assertEquals("1", completed["record_id"])
        assertEquals("create", completed["mode"])
        assertEquals("true", completed["has_title"])
        assertEquals("true", completed["has_content"])
        assertEquals("false", completed["has_end_date"])
        assertEquals("false", completed["has_tags"])
        assertEquals("true", completed["has_photos"])
        assertFalse(analytics.events.toString().contains("private"))
    }

    @Test fun `failed queue never emits successful creation`() = runBlocking {
        val analytics = RecordingAnalytics()
        val backing = fake()
        val repository = object : TripRecordRepository by backing {
            override suspend fun createTripRecord(draft: TripRecordDraft) =
                Result.failure<com.mapmory.shared.domain.model.TripRecordData>(IllegalStateException())
        }
        val saver = BackgroundTripRecordSaver(
            analytics = analytics,
            repository = AnalyticsTripRecordRepository(repository, analytics),
            scope = CoroutineScope(coroutineContext), automaticRetryDelayMillis = 0,
        )
        val queuedId = saver.enqueue(draft(), "location")
        withTimeout(5_000) { saver.saves.first { it.singleOrNull()?.status == BackgroundSaveStatus.FAILED } }
        assertEquals(0, analytics.events.count { it.first == MapmoryAnalyticsEvent.RECORD_SAVE_COMPLETED })
        assertEquals(2, analytics.events.count { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.RECORD_SAVE_FAILED })
        saver.dismissFailure(queuedId)
        saver.dismissFailure(queuedId)
        saver.retry(queuedId)
        assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.RECORD_SAVE_DISCARDED })
        assertEquals(0, analytics.events.count { it.first == MapmoryAnalyticsEvent.RECORD_SAVE_RETRY })
    }

    @Test fun `container wires create edit delete and read without treating editor read as a view`() = runBlocking {
        val analytics = RecordingAnalytics()
        val container = createAppContainer(tripRecordRepository = fake(), analytics = analytics)
        try {
            val repository = container.tripRecordRepository
            val saved = repository.createTripRecord(draft()).getOrThrow()
            repository.getTripRecord(saved.id).getOrThrow()
            repository.updateTripRecord(saved.id, draft()).getOrThrow()
            repository.deleteTripRecord(saved.id).getOrThrow()
            // Edit completion remains at the legacy UI boundary; repository must not duplicate it.
            assertFalse(analytics.events.any { it.first == MapmoryAnalyticsEvent.RECORD_SAVE_COMPLETED })
            assertEquals(1, analytics.events.count { it.first == MapmoryAnalyticsEvent.RECORD_DELETED })
            assertFalse(analytics.events.any { it.first == MapmoryAnalyticsEvent.RECORD_VIEWED })
            assertTrue(container.tripRecordRepository is ProgressReportingTripRecordRepository)
        } finally { container.close() }
    }
}
