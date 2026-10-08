package com.mapmory.shared.presentation.triprecord.viewmodel

import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.remote.model.ProblemFieldErrorDto
import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.domain.model.Location
import com.mapmory.shared.domain.model.LocationType
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceReference
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.model.PlaceSelection
import com.mapmory.shared.domain.model.TripRecordData
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordMedia
import com.mapmory.shared.domain.model.TripRecordPhotoRules
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.domain.repository.PlaceRepository
import com.mapmory.shared.domain.usecase.CreateTripRecordUseCase
import com.mapmory.shared.domain.usecase.CreateTagUseCase
import com.mapmory.shared.domain.usecase.GetTripRecordsUseCase
import com.mapmory.shared.domain.usecase.GetTagsUseCase
import com.mapmory.shared.domain.usecase.UpdateTripRecordUseCase
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.triprecord.state.TripRecordEditorErrorTarget
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripRecordEditorViewModelTest {
    @Test
    fun `장소_검색_서비스를_사용할_수_없으면_내부_설정_오류_대신_안내를_표시한다`() = runSuspend {
        val placeRepository = object : PlaceRepository {
            override suspend fun searchPlaces(query: String) = Result.failure<List<PlaceCandidate>>(
                MapmoryApiException(
                    statusCode = 503,
                    code = "PLACE_PROVIDER_UNAVAILABLE",
                    title = "장소 검색을 사용할 수 없습니다.",
                    detail = "GEOAPIFY_API_KEY가 설정되지 않았습니다.",
                    instance = "/api/v1/places/search",
                    errors = emptyList(),
                ),
            )

            override suspend fun selectPlace(placeId: String) = Result.failure<PlaceSelection>(
                IllegalStateException("장소를 선택하지 않았습니다."),
            )
        }
        val repository = FakeTripRecordRepository { "2026-10-01T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
            placeRepository = placeRepository,
        )
        viewModel.startCreating(location = null)

        viewModel.searchPlaces("한강공원")

        assertEquals(
            "장소 검색을 지금 사용할 수 없어요. 잠시 후 다시 시도해 주세요.",
            viewModel.uiState.placeSearchErrorMessage,
        )
        assertTrue(viewModel.uiState.hasSearchedPlaces)
        assertFalse(viewModel.uiState.isSearchingPlaces)
    }

    @Test
    fun `장소를_선택하면_추천_행정구역과_장소_연결을_기록에_저장한다`() = runSuspend {
        val recordRepository = FakeTripRecordRepository { "2026-10-01T00:00:00Z" }
        val candidate = PlaceCandidate(
            placeId = "geoapify-place-id",
            name = "판교역",
            address = "경기도 성남시 분당구",
            attribution = "© OpenStreetMap contributors",
            attributionUrl = "https://www.openstreetmap.org/copyright",
        )
        val placeRepository = object : PlaceRepository {
            override suspend fun searchPlaces(query: String) = Result.success(listOf(candidate))

            override suspend fun selectPlace(placeId: String) = Result.success(
                PlaceSelection(
                    place = PlaceReference(placeId, "판교역"),
                    countryCode = "KR",
                    suggestedRegion = PlaceRegionSuggestion("KR", "41", "41135"),
                    manualRegionRequired = false,
                ),
            )
        }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(recordRepository),
            updateTripRecord = UpdateTripRecordUseCase(recordRepository),
            regionCatalog = StaticRegionCatalog(),
            placeRepository = placeRepository,
        )
        viewModel.startCreating(location = null)

        val selectedLocation = viewModel.selectPlace(candidate)

        assertEquals("41130", selectedLocation?.regionCode)
        assertEquals(candidate.placeId, viewModel.uiState.selectedPlace?.placeId)
        viewModel.addPhotos(listOf(selectedPhoto("content://photo/pangyo")))
        viewModel.updateStartDate("2026-10-01")
        assertTrue(viewModel.save())
        assertEquals(
            candidate.placeId,
            recordRepository.getTripRecord(1).getOrThrow().place?.placeId,
        )
    }

    @Test
    fun `장소의_행정구역을_직접_선택해도_장소_연결을_기록에_저장한다`() = runSuspend {
        val recordRepository = FakeTripRecordRepository { "2026-10-01T00:00:00Z" }
        val candidate = PlaceCandidate(
            placeId = "geoapify-place-id",
            name = "판교역",
            address = null,
            attribution = null,
            attributionUrl = null,
        )
        val placeRepository = object : PlaceRepository {
            override suspend fun searchPlaces(query: String) = Result.success(listOf(candidate))

            override suspend fun selectPlace(placeId: String) = Result.success(
                PlaceSelection(
                    place = PlaceReference(placeId, "판교역"),
                    countryCode = "KR",
                    suggestedRegion = null,
                    manualRegionRequired = true,
                ),
            )
        }
        val regionCatalog = StaticRegionCatalog()
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(recordRepository),
            updateTripRecord = UpdateTripRecordUseCase(recordRepository),
            regionCatalog = regionCatalog,
            placeRepository = placeRepository,
        )
        viewModel.startCreating(location = null)

        assertNull(viewModel.selectPlace(candidate))
        assertTrue(viewModel.uiState.manualRegionRequired)
        val selectedRegion = requireNotNull(regionCatalog.findDistrict("KR-41", "41130"))
        viewModel.selectLocation(selectedRegion)
        viewModel.addPhotos(listOf(selectedPhoto("content://photo/pangyo")))
        viewModel.updateStartDate("2026-10-01")

        assertFalse(viewModel.uiState.manualRegionRequired)
        assertTrue(viewModel.save())
        val savedRecord = recordRepository.getTripRecord(1).getOrThrow()
        assertEquals(selectedRegion.id, savedRecord.locationId)
        assertEquals(candidate.placeId, savedRecord.place?.placeId)
    }

    @Test
    fun `수정 저장은 해제한 기존 사진을 빼고 새 사진을 추가한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-09-30T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )
        val location = Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT)
        viewModel.startCreating(location)
        viewModel.addPhotos(listOf(selectedPhoto("old-1"), selectedPhoto("old-2")))
        viewModel.useSelectedPhotoDates("2026-09-30")
        assertTrue(viewModel.save())
        viewModel.startEditing(repository.getTripRecord(1).getOrThrow(), location)
        viewModel.removeMediaObjectKey("old-1")
        viewModel.addPhotos(listOf(selectedPhoto("new-1")))
        assertTrue(viewModel.save())
        assertEquals(listOf("old-2", "new-1"), repository.getTripRecord(1).getOrThrow().media.map { it.objectKey })
    }

    @Test
    fun `사진만으로_생성할_때_촬영일_범위를_사용하고_제목은_비워_둔다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-09-30T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )
        viewModel.startCreating(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
        viewModel.addPhotos(listOf(
            selectedPhoto("one").copy(capturedAt = "2026.09.16"),
            selectedPhoto("two").copy(capturedAt = "2026.09.14"),
            selectedPhoto("three").copy(capturedAt = null),
        ))
        viewModel.useSelectedPhotoDates("2026-09-30")
        assertEquals("2026-09-14", viewModel.uiState.startDate)
        assertEquals("2026-09-16", viewModel.uiState.endDate)
        assertEquals("", viewModel.uiState.title)
        assertTrue(viewModel.save())
        assertEquals(3, repository.getTripRecord(1).getOrThrow().media.size)
    }

    @Test
    fun `촬영일이_없는_사진은_오늘을_사용한다`() {
        val repository = FakeTripRecordRepository { "2026-09-30T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )
        viewModel.addPhotos(listOf(selectedPhoto("one").copy(capturedAt = null)))
        viewModel.useSelectedPhotoDates("2026-09-30")
        assertEquals("2026-09-30", viewModel.uiState.startDate)
        assertEquals("", viewModel.uiState.endDate)
    }

    @Test
    fun `직접_만든_태그를_선택해_기록에_저장한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-31T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
            getTags = GetTagsUseCase(repository),
            createTag = CreateTagUseCase(repository),
        )

        viewModel.initialize(recordId = null, selectedLocation = null)
        viewModel.updateTagInput(" 라멘맛집 ")
        viewModel.createAndSelectTag()
        assertTrue(repository.getTags().getOrThrow().isEmpty())
        viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
        viewModel.updateTitle("서울 여행")
        viewModel.updateStartDate("2026-08-31")
        viewModel.addPhotos(listOf(selectedPhoto("content://photo/tagged")))

        assertTrue(viewModel.save())
        assertEquals("라멘맛집", repository.getTags().getOrThrow().single().name)
        assertEquals("라멘맛집", repository.getTripRecord(1).getOrThrow().tags.single().name)
    }

    @Test
    fun `저장하지_않고_나가면_직접_만든_태그는_저장되지_않는다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-31T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
            getTags = GetTagsUseCase(repository),
            createTag = CreateTagUseCase(repository),
        )

        viewModel.initialize(recordId = null, selectedLocation = null)
        viewModel.updateTagInput(" 라멘맛집 ")
        viewModel.createAndSelectTag()

        assertEquals(listOf("라멘맛집"), viewModel.uiState.pendingTagNames)
        assertEquals(setOf("라멘맛집"), viewModel.uiState.selectedPendingTagNames)
        assertTrue(repository.getTags().getOrThrow().isEmpty())

        viewModel.reset()

        assertTrue(repository.getTags().getOrThrow().isEmpty())
    }

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
        viewModel.addPhotos(listOf(selectedPhoto("content://photo/pending")))
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
            viewModel.addPhotos(listOf(selectedPhoto("content://photo/create")))

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
    fun `제목과_내용과_종료일과_태그가_없어도_기록을_저장한다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )

        viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
        viewModel.updateStartDate("2026-08-01")
        viewModel.addPhotos(listOf(selectedPhoto("content://photo/minimum")))

        assertTrue(viewModel.save())
        val record = repository.getTripRecords(TripRecordQuery()).getOrThrow().records.single()
        assertEquals("", record.title)
        assertEquals("", record.content)
        assertEquals(null, record.endDate)
        assertTrue(record.tags.isEmpty())
    }

    @Test
    fun `사진이_없으면_기록을_저장할_수_없다`() = runSuspend {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )

        viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
        viewModel.updateStartDate("2026-08-01")

        assertFalse(viewModel.save())
        assertEquals(
            TripRecordPhotoRules.RequiredMessage,
            viewModel.uiState.fieldErrors[TripRecordEditorErrorTarget.PHOTOS],
        )
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
            viewModel.addPhotos(listOf(selectedPhoto("content://photo/date")))

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
            assertTrue(viewModel.uiState.fieldErrors.isEmpty())

            viewModel.touchLocation()
            assertEquals(
                mapOf(TripRecordEditorErrorTarget.LOCATION to "장소를 선택해 주세요."),
                viewModel.uiState.fieldErrors,
            )

            viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
            assertTrue(viewModel.uiState.fieldErrors.isEmpty())
            viewModel.updateTitle("서울 여행")
            assertTrue(viewModel.uiState.fieldErrors.isEmpty())

            viewModel.updateTitle("가".repeat(201))
            assertTrue(viewModel.uiState.fieldErrors.isEmpty())
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
        viewModel.addPhotos(listOf(selectedPhoto("content://photo/location")))

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

    @Test
    fun `기록_수정에서는_기존_사진을_포함해_최대_100장만_추가한다`() {
        val repository = FakeTripRecordRepository { "2026-08-07T00:00:00Z" }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )
        val location = Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT)
        viewModel.startEditing(
            record = TripRecordData(
                id = 1,
                locationId = location.id,
                title = "서울 여행",
                content = "",
                startDate = "2026-08-01",
                endDate = null,
                media = (1..99).map { index ->
                    TripRecordMedia(
                        id = index.toLong(),
                        objectKey = "records/1/existing-$index.jpg",
                        sortOrder = index - 1,
                        url = null,
                    )
                },
                createdAt = "2026-08-01T00:00:00Z",
                updatedAt = "2026-08-01T00:00:00Z",
            ),
            location = location,
        )

        viewModel.addPhotos(
            listOf(
                selectedPhoto("content://new/1"),
                selectedPhoto("content://new/2"),
            ),
        )

        assertEquals(TripRecordPhotoRules.MaxPhotosPerRecord, viewModel.uiState.selectedPhotos.size)
        assertEquals(
            TripRecordPhotoRules.LimitMessage,
            viewModel.uiState.fieldErrors[TripRecordEditorErrorTarget.PHOTOS],
        )
        assertTrue(viewModel.uiState.selectedPhotos.any { photo -> photo.id == "content://new/1" })
        assertTrue(viewModel.uiState.selectedPhotos.none { photo -> photo.id == "content://new/2" })

        viewModel.removeMediaObjectKey("records/1/existing-1.jpg")
        viewModel.addPhotos(listOf(selectedPhoto("content://new/2")))

        assertEquals(TripRecordPhotoRules.MaxPhotosPerRecord, viewModel.uiState.selectedPhotos.size)
        assertNull(viewModel.uiState.fieldErrors[TripRecordEditorErrorTarget.PHOTOS])
    }

    @Test
    fun `사진_업로드_제한_오류는_사진_컴포넌트의_오류로_분류한다`() {
        val error = MapmoryApiException(
            statusCode = 400,
            code = "TOO_MANY_FILES",
            title = "파일 개수가 너무 많습니다.",
            detail = "한 번에 업로드할 수 있는 최대 파일 개수를 초과했습니다.",
            instance = "/api/v1/uploads/presigned-urls",
            errors = emptyList(),
        )

        assertEquals(
            mapOf(
                TripRecordEditorErrorTarget.PHOTOS to
                    "한 번에 업로드할 수 있는 최대 파일 개수를 초과했습니다.",
            ),
            error.toEditorFieldErrors(),
        )
    }

    @Test
    fun `사진_업로드_실패는_폼_하단이_아니라_사진_필드에_저장한다`() = runSuspend {
        val error = MapmoryApiException(
            statusCode = 400,
            code = "TOO_MANY_FILES",
            title = "파일 개수가 너무 많습니다.",
            detail = "한 번에 업로드할 수 있는 최대 파일 개수를 초과했습니다.",
            instance = "/api/v1/uploads/presigned-urls",
            errors = emptyList(),
        )
        val delegate = FakeTripRecordRepository { "2026-08-31T00:00:00Z" }
        val repository = object : TripRecordRepository by delegate {
            override suspend fun createTripRecord(draft: TripRecordDraft): Result<TripRecordData> =
                Result.failure(error)
        }
        val viewModel = TripRecordEditorViewModel(
            createTripRecord = CreateTripRecordUseCase(repository),
            updateTripRecord = UpdateTripRecordUseCase(repository),
        )
        viewModel.selectLocation(Location(101, 1, 1, "11680", "강남구", LocationType.DISTRICT))
        viewModel.updateTitle("서울 여행")
        viewModel.updateStartDate("2026-08-31")
        viewModel.addPhotos(listOf(selectedPhoto("content://photo/upload")))

        assertFalse(viewModel.save())
        assertEquals(
            "한 번에 업로드할 수 있는 최대 파일 개수를 초과했습니다.",
            viewModel.uiState.fieldErrors[TripRecordEditorErrorTarget.PHOTOS],
        )
        assertNull(viewModel.uiState.generalErrorMessage)
    }

    @Test
    fun `서버의_필드_오류는_각_입력_컴포넌트의_오류로_분류한다`() {
        val error = MapmoryApiException(
            statusCode = 400,
            code = "VALIDATION_ERROR",
            title = "요청 값이 올바르지 않습니다.",
            detail = null,
            instance = "/api/v1/travel-records",
            errors = listOf(
                ProblemFieldErrorDto("title", "제목을 확인해 주세요."),
                ProblemFieldErrorDto("content", "내용을 확인해 주세요."),
                ProblemFieldErrorDto("files[0].fileSize", "사진 크기를 확인해 주세요."),
                ProblemFieldErrorDto("tagIds", "태그를 확인해 주세요."),
            ),
        )

        assertEquals(
            mapOf(
                TripRecordEditorErrorTarget.TITLE to "제목을 확인해 주세요.",
                TripRecordEditorErrorTarget.CONTENT to "내용을 확인해 주세요.",
                TripRecordEditorErrorTarget.PHOTOS to "사진 크기를 확인해 주세요.",
                TripRecordEditorErrorTarget.TAGS to "태그를 확인해 주세요.",
            ),
            error.toEditorFieldErrors(),
        )
    }

}

private fun selectedPhoto(id: String): SelectedPhoto = SelectedPhoto(
    id = id,
    displayName = id.substringAfterLast('/'),
    previewBytes = null,
    originalBytes = byteArrayOf(0x01),
)
