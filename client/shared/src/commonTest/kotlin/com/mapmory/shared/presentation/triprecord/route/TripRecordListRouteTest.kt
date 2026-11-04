package com.mapmory.shared.presentation.triprecord.route

import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordPage
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.usecase.GetTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordsUseCase
import com.mapmory.shared.presentation.triprecord.TripRecordListLayout
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoCollectionIndexState
import com.mapmory.shared.presentation.triprecord.viewmodel.TripRecordListViewModel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TripRecordListRouteTest {
    @Test
    fun `컬렉션_목록_실패_후_재시도_성공하면_여행지_인덱스를_불러온다`() = runBlocking {
        val source = FakeTripRecordRepository { "2026-10-08T00:00:00Z" }
        source.createTripRecord(
            TripRecordDraft(
                locationId = 101,
                startDate = "2026-08-01",
                mediaObjectKeys = listOf("seoul.jpg"),
                title = "서울 여행",
            ),
        ).getOrThrow()
        var requestCount = 0
        val repository = object : TripRecordRepository by source {
            override suspend fun getTripRecords(query: TripRecordQuery): Result<TripRecordPage> {
                requestCount++
                if (requestCount == 1) return Result.failure(IllegalStateException("오프라인"))
                return source.getTripRecords(query)
            }
        }
        val viewModel = TripRecordListViewModel(
            GetTripRecordsUseCase(repository),
            GetTripRecordUseCase(repository),
        )

        viewModel.refreshIfNeeded(101, dataRevision = 0)
        assertIs<TripRecordListUiState.Error>(viewModel.uiState)
        assertIs<TripRecordPhotoCollectionIndexState.Idle>(viewModel.photoCollectionIndexState)

        retryTripRecordList(viewModel, 101, TripRecordListLayout.COLLECTION)

        assertIs<TripRecordListUiState.Success>(viewModel.uiState)
        val loadedIndex = assertIs<TripRecordPhotoCollectionIndexState.Loaded>(viewModel.photoCollectionIndexState)
        assertEquals(1, loadedIndex.records.size)
        assertEquals(3, requestCount)
    }
}
