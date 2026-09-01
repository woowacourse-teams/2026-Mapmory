package com.mapmory.shared.presentation.triprecord.viewmodel

import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.usecase.DeleteTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordUseCase
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TripRecordDetailViewModelTest {
    @Test
    fun `로드는_성공과_실패에_따라_상태를_변경한다`() {
        runSuspend {
            val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
            val record = repository.createTripRecord(
                TripRecordDraft(
                    locationId = 101,
                    title = "서울 여행",
                    content = "한강을 걸었다.",
                    startDate = "2026-08-01",
                    endDate = null,
                    mediaObjectKeys = emptyList(),
                ),
            ).getOrThrow()
            var changeCount = 0
            val viewModel = TripRecordDetailViewModel(
                getTripRecord = GetTripRecordUseCase(repository),
                deleteTripRecord = DeleteTripRecordUseCase(repository),
                onTripRecordsChanged = { changeCount += 1 },
            )

            viewModel.load(record.id)

            val success = assertIs<TripRecordDetailUiState.Success>(viewModel.uiState)
            assertEquals("서울 여행", success.record.title)

            assertEquals(true, viewModel.delete())
            assertEquals(1, changeCount)
            viewModel.load(record.id)

            assertIs<TripRecordDetailUiState.Error>(viewModel.uiState)
        }
    }
}
