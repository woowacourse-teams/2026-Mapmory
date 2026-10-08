package com.mapmory.shared.presentation.triprecord.viewmodel

import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.usecase.GetTripRecordsUseCase
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoadResult
import com.mapmory.shared.presentation.triprecord.thumbnail.TripRecordThumbnailLoader
import com.mapmory.shared.runSuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TripRecordListViewModelTest {
    @Test
    fun `재진입은_동일_리비전의_본문과_썸네일을_재조회하지_않는다`() = runBlocking {
        val repository = ThumbnailListRepository()
        var thumbnailRequests = 0
        val viewModel = TripRecordListViewModel(
            GetTripRecordsUseCase(repository),
            thumbnailLoader = TripRecordThumbnailLoader {
                thumbnailRequests++
                TripRecordThumbnailLoadResult.Success(byteArrayOf(1))
            },
        )
        viewModel.refreshIfNeeded(null, 0)
        val visible = viewModel.uiState
        viewModel.refreshIfNeeded(null, 0)
        assertEquals(visible, viewModel.uiState)
        assertEquals(1, repository.listRequestCount)
        assertEquals(1, thumbnailRequests)

        viewModel.refreshIfNeeded(null, 1)
        assertEquals(2, repository.listRequestCount)
        viewModel.refreshIfNeeded(101, 1)
        assertEquals(3, repository.listRequestCount)
        assertEquals(101, viewModel.query.locationId)
    }

    @Test
    fun `선택한_태그_ID로_기록을_필터링한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-31T00:00:00Z" }
        val family = repository.createTag("가족").getOrThrow()
        val food = repository.createTag("맛집").getOrThrow()
        repository.createTripRecord(
            TripRecordDraft(
                locationId = 101,
                startDate = "2026-08-01",
                mediaObjectKeys = listOf("family.jpg"),
                title = "가족 여행",
                content = "",
                tagIds = listOf(family.id),
            ),
        )
        repository.createTripRecord(
            TripRecordDraft(
                locationId = 101,
                startDate = "2026-08-02",
                mediaObjectKeys = listOf("food.jpg"),
                title = "맛집 여행",
                content = "",
                tagIds = listOf(food.id),
            ),
        )
        val viewModel = TripRecordListViewModel(
            getTripRecords = GetTripRecordsUseCase(repository),
            getTags = GetTagsUseCase(repository),
        )

        viewModel.initialize(locationId = null)
        viewModel.selectTag(food.id)

        val state = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        assertEquals("맛집 여행", state.records.single().title)
        assertEquals(food.id, viewModel.query.tagId)
    }

    @Test
    fun `목록_본문을_먼저_표시하고_썸네일은_완료되는_대로_채운다`() = runBlocking {
        val thumbnailGate = CompletableDeferred<Unit>()
        val repository = ThumbnailListRepository()
        val viewModel = TripRecordListViewModel(
            getTripRecords = GetTripRecordsUseCase(repository),
            thumbnailLoader = TripRecordThumbnailLoader {
                thumbnailGate.await()
                TripRecordThumbnailLoadResult.Success(byteArrayOf(0x01, 0x02))
            },
        )

        val loadJob = launch { viewModel.load() }
        yield()

        val beforeThumbnail = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        assertEquals("여행 1", beforeThumbnail.records.single().title)
        assertEquals(0, beforeThumbnail.records.single().photos.size)

        thumbnailGate.complete(Unit)
        loadJob.join()

        val afterThumbnail = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        assertContentEquals(
            byteArrayOf(0x01, 0x02),
            afterThumbnail.records.single().photos.single().previewBytes?.bytesForDecoding(),
        )
    }

    @Test
    fun `여러_썸네일_URL이_만료돼도_목록은_한_번만_갱신한다`() = runBlocking {
        val repository = ThumbnailListRepository(recordCount = 2, expiresFirstResponse = true)
        val viewModel = TripRecordListViewModel(
            getTripRecords = GetTripRecordsUseCase(repository),
            thumbnailLoader = TripRecordThumbnailLoader { record ->
                if ("expired" in requireNotNull(record.thumbnailUrl)) {
                    TripRecordThumbnailLoadResult.UrlExpired
                } else {
                    TripRecordThumbnailLoadResult.Success(byteArrayOf(record.id.toByte()))
                }
            },
        )

        viewModel.load()

        assertEquals(2, repository.listRequestCount)
        val state = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        assertEquals(2, state.records.count { it.photos.singleOrNull()?.previewBytes != null })
    }

    @Test
    fun `새로고침은_기록_변경_후_현재_필터를_다시_조회한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        val original = repository.createTripRecord(
            TripRecordDraft(
                locationId = 101,
                title = "수정 전",
                content = "",
                startDate = "2026-08-01",
                endDate = null,
                mediaObjectKeys = listOf("before.jpg"),
            ),
        ).getOrThrow()
        val viewModel = TripRecordListViewModel(GetTripRecordsUseCase(repository))
        viewModel.initialize(locationId = 101)

        repository.updateTripRecord(
            original.id,
            TripRecordDraft(
                locationId = 101,
                title = "수정 후",
                content = "",
                startDate = "2026-08-01",
                endDate = null,
                mediaObjectKeys = listOf("after.jpg"),
            ),
        ).getOrThrow()
        viewModel.refresh(locationId = 101)

        val state = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        assertEquals("수정 후", state.records.single().title)
        assertEquals(101, viewModel.query.locationId)
    }

    @Test
    fun `같은_조건으로_새로고침하는_동안_기존_목록을_유지한다`() = runBlocking {
        val source = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        source.createTripRecord(
            TripRecordDraft(
                locationId = 101,
                title = "서울 여행",
                content = "",
                startDate = "2026-08-01",
                endDate = null,
                mediaObjectKeys = listOf("seoul.jpg"),
            ),
        ).getOrThrow()
        val refreshGate = CompletableDeferred<Boolean>()
        val repository = BlockingRefreshRepository(source, refreshGate)
        val viewModel = TripRecordListViewModel(GetTripRecordsUseCase(repository))
        viewModel.load()

        val refreshJob = launch { viewModel.load() }
        yield()

        val refreshing = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        assertTrue(refreshing.isRefreshing)
        assertEquals("서울 여행", refreshing.records.single().title)

        refreshGate.complete(true)
        refreshJob.join()

        val refreshed = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        assertTrue(!refreshed.isRefreshing)
        assertEquals(null, refreshed.refreshError)
        assertEquals("서울 여행", refreshed.records.single().title)
    }

    @Test
    fun `새로고침에_실패해도_기존_목록과_재시도_상태를_유지한다`() = runBlocking {
        val source = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        source.createTripRecord(
            TripRecordDraft(
                locationId = 101,
                title = "서울 여행",
                content = "",
                startDate = "2026-08-01",
                endDate = null,
                mediaObjectKeys = listOf("seoul.jpg"),
            ),
        ).getOrThrow()
        val refreshGate = CompletableDeferred<Boolean>()
        val repository = BlockingRefreshRepository(source, refreshGate)
        val viewModel = TripRecordListViewModel(GetTripRecordsUseCase(repository))
        viewModel.load()

        val refreshJob = launch { viewModel.load() }
        yield()
        refreshGate.complete(false)
        refreshJob.join()

        val state = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        assertTrue(!state.isRefreshing)
        assertEquals("목록 갱신 실패", state.refreshError)
        assertEquals("서울 여행", state.records.single().title)
    }

    @Test
    fun `경로를_반복_초기화해도_현재_필터를_유지한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        val viewModel = TripRecordListViewModel(GetTripRecordsUseCase(repository))

        viewModel.initialize(locationId = 101)
        viewModel.initialize(locationId = 101)

        assertEquals(101, viewModel.query.locationId)
    }

    @Test
    fun `로드는_성공과_실패에_따라_상태를_변경한다`() {
        runSuspend {
            val repository = FakeTripRecordRepository(
                now = { "2026-08-07T00:00:00Z" },
            )
            repository.createTripRecord(
                TripRecordDraft(
                    locationId = 101,
                    title = "서울 여행",
                    content = "한강을 걸었다.",
                    startDate = "2026-08-01",
                    endDate = null,
                    mediaObjectKeys = listOf("seoul.jpg"),
                ),
            )
            repository.createTripRecord(
                TripRecordDraft(
                    locationId = 102,
                    title = "부산 여행",
                    content = "바다를 보았다.",
                    startDate = "2026-08-02",
                    endDate = null,
                    mediaObjectKeys = listOf("busan.jpg"),
                ),
            )
            val viewModel = TripRecordListViewModel(GetTripRecordsUseCase(repository))

            viewModel.load(TripRecordQuery(size = 1))

            val success = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
            assertEquals("서울 여행", success.records.single().title)

            viewModel.nextPage()
            val nextPage = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
            assertEquals("부산 여행", nextPage.records.single().title)

            viewModel.previousPage()
            val previousPage = assertIs<TripRecordListUiState.Success>(viewModel.uiState)
            assertEquals("서울 여행", previousPage.records.single().title)

            viewModel.load(TripRecordQuery(size = 0))

            assertIs<TripRecordListUiState.Error>(viewModel.uiState)
        }
    }
}

private class ThumbnailListRepository(
    private val recordCount: Int = 1,
    private val expiresFirstResponse: Boolean = false,
) : TripRecordRepository {
    var listRequestCount = 0
        private set

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> {
        listRequestCount += 1
        val signature = if (expiresFirstResponse && listRequestCount == 1) "expired" else "fresh"
        return Result.success(
            TripRecordPage(
                records = (1..recordCount).map { id ->
                    TripRecordSummary(
                        id = id.toLong(),
                        title = "여행 $id",
                        startDate = "2026-08-27",
                        endDate = null,
                        thumbnailUrl =
                            "https://bucket.example.com/travel-records/10/$id.jpg?$signature",
                    )
                },
                page = query.page,
                size = query.size,
                totalElements = recordCount.toLong(),
                totalPages = 1,
            ),
        )
    }

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

private class BlockingRefreshRepository(
    private val source: TripRecordRepository,
    private val refreshGate: CompletableDeferred<Boolean>,
) : TripRecordRepository by source {
    private var requestCount = 0

    override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> {
        requestCount += 1
        if (requestCount == 2 && !refreshGate.await()) {
            return Result.failure(IllegalStateException("목록 갱신 실패"))
        }
        return source.getTripRecords(query)
    }
}
