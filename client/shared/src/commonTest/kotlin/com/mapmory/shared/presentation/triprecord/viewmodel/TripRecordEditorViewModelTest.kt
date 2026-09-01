package com.mapmory.shared.presentation.triprecord.viewmodel

import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.usecase.CreateTripRecordUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordsUseCase
import com.mapmory.shared.domain.usecase.UpdateTripRecordUseCase
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorErrorTarget
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripRecordEditorViewModelTest {
    @Test
    fun `경로를_반복_초기화해도_작성_초안을_유지한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )
        val gangnam = Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT)

        viewModel.initialize(recordId = null, selectedLocation = gangnam)
        viewModel.updateTitle("재생성 뒤에도 남을 제목")
        viewModel.initialize(recordId = null, selectedLocation = gangnam)

        assertEquals("재생성 뒤에도 남을 제목", viewModel.uiState.title)
    }

    @Test
    fun `저장은_대기_중인_사진을_기다리고_사용자_확인_후_제외한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )
        viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
        viewModel.updateTitle("서울 여행")
        viewModel.updateStartDate("2026-08-01")
        viewModel.setPhotoLoading(true)

        assertFalse(viewModel.save())
        assertTrue(repository.getTripRecords(TripRecordQuery()).getOrThrow().records.isEmpty())

        viewModel.setPhotoLoading(false)
        assertTrue(viewModel.save())
        assertEquals(
            "서울 여행",
            repository.getTripRecords(TripRecordQuery()).getOrThrow().records.single().title,
        )
    }

    @Test
    fun `저장은_여행_기록을_생성하고_수정한다`() {
        runSuspend {
            val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
            val viewModel = TripRecordEditorViewModel(
                createTripRecord = CreateTripRecordUseCase(repository),
                updateTripRecord = UpdateTripRecordUseCase(repository),
            )

            viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
            viewModel.updateTitle("서울 여행")
            viewModel.updateContent("한강을 걸었다.")
            viewModel.updateStartDate("2026-08-01")

            assertTrue(viewModel.save())
            assertEquals(
                "서울 여행",
                GetTripRecordsUseCase(repository)(TripRecordQuery()).getOrThrow().records.single().title,
            )

            val recordId = repository.getTripRecords(TripRecordQuery()).getOrThrow().records.single().id
            val record = repository.getTripRecord(recordId).getOrThrow()
            viewModel.startEditing(
                record = record,
                location = Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT),
            )
            viewModel.clearLocation()
            assertNull(viewModel.uiState.selectedLocation)
            viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
            viewModel.updateTitle("서울 여름 여행")

            assertTrue(viewModel.save())
            assertEquals("서울 여름 여행", repository.getTripRecord(record.id).getOrThrow().title)
        }
    }

    @Test
    fun `저장은_시작일을_요구하고_잘못된_날짜_범위를_거부한다`() {
        runSuspend {
            val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
            val viewModel = TripRecordEditorViewModel(
                createTripRecord = CreateTripRecordUseCase(repository),
                updateTripRecord = UpdateTripRecordUseCase(repository),
            )

            viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
            viewModel.updateTitle("서울 여행")
            viewModel.updateEndDate("2026-08-01")

            assertFalse(viewModel.save())
            assertEquals("시작일을 입력해 주세요.", viewModel.uiState.errorMessage)
            assertEquals(TripRecordEditorErrorTarget.START_DATE, viewModel.uiState.errorTarget)

            viewModel.updateStartDate("2026-08-01")
            assertTrue(viewModel.save())

            viewModel.updateStartDate("2026-08-02")
            assertEquals("종료일은 시작일보다 빠를 수 없습니다.", viewModel.uiState.errorMessage)
            assertEquals(TripRecordEditorErrorTarget.START_DATE, viewModel.uiState.errorTarget)
            assertFalse(viewModel.save())
            assertEquals("종료일은 시작일보다 빠를 수 없습니다.", viewModel.uiState.errorMessage)
            assertEquals(TripRecordEditorErrorTarget.END_DATE, viewModel.uiState.errorTarget)

            viewModel.updateStartDate("2026-02-29")
            viewModel.updateEndDate("")
            assertEquals("올바른 시작일을 입력해 주세요.", viewModel.uiState.errorMessage)
            assertEquals(TripRecordEditorErrorTarget.START_DATE, viewModel.uiState.errorTarget)
            assertFalse(viewModel.save())
            assertEquals("올바른 시작일을 입력해 주세요.", viewModel.uiState.errorMessage)
            assertEquals(TripRecordEditorErrorTarget.START_DATE, viewModel.uiState.errorTarget)
        }
    }

    @Test
    fun `수정_중에는_건드린_필드의_오류만_즉시_표시한다`() {
        runSuspend {
            val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
            val viewModel = TripRecordEditorViewModel(
                createTripRecord = CreateTripRecordUseCase(repository),
                updateTripRecord = UpdateTripRecordUseCase(repository),
            )
            assertFalse(viewModel.uiState.isDirty)
            viewModel.updateContent("작성 시작")

            assertTrue(viewModel.uiState.isDirty)
            assertTrue(viewModel.uiState.fieldErrors.isEmpty())

            viewModel.updateTitle(" ")
            assertEquals(
                mapOf(TripRecordEditorErrorTarget.TITLE to "제목을 입력해 주세요."),
                viewModel.uiState.fieldErrors,
            )

            viewModel.touchLocation()
            assertEquals(
                mapOf(
                    TripRecordEditorErrorTarget.LOCATION to "장소를 선택해 주세요.",
                    TripRecordEditorErrorTarget.TITLE to "제목을 입력해 주세요.",
                ),
                viewModel.uiState.fieldErrors,
            )

            viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
            assertEquals(
                mapOf(TripRecordEditorErrorTarget.TITLE to "제목을 입력해 주세요."),
                viewModel.uiState.fieldErrors,
            )
            viewModel.updateTitle("서울 여행")
            assertTrue(viewModel.uiState.fieldErrors.isEmpty())

            viewModel.updateTitle("가".repeat(201))
            assertEquals(
                mapOf(TripRecordEditorErrorTarget.TITLE to "제목은 200자 이하여야 합니다."),
                viewModel.uiState.fieldErrors,
            )
        }
    }

    @Test
    fun `국내_시도는_여행지로_선택되지_않는다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )
        viewModel.selectLocation(Location(1, 1, null, "KR-11", "서울특별시", LocationType.PROVINCE))
        viewModel.updateTitle("서울 여행")
        viewModel.updateStartDate("2026-08-01")

        assertNull(viewModel.uiState.selectedLocation)
        assertFalse(viewModel.save())
        assertEquals(
            "장소를 선택해 주세요.",
            viewModel.uiState.fieldErrors[TripRecordEditorErrorTarget.LOCATION],
        )
    }

    @Test
    fun `미디어_Object_Key를_추가하고_삭제할_수_있다`() {
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(FakeTripRecordRepository { "2026-08-07T00:00:00Z" }),
            updateTripRecord = UpdateTripRecordUseCase(FakeTripRecordRepository { "2026-08-07T00:00:00Z" }),
        )

        viewModel.addMediaObjectKey(" records/1/photo.jpg ")
        viewModel.addMediaObjectKey("records/1/photo.jpg")
        viewModel.addMediaObjectKey(" ")

        assertEquals(listOf("records/1/photo.jpg"), viewModel.uiState.mediaObjectKeys)

        viewModel.removeMediaObjectKey("records/1/photo.jpg")

        assertTrue(viewModel.uiState.mediaObjectKeys.isEmpty())
    }
}
