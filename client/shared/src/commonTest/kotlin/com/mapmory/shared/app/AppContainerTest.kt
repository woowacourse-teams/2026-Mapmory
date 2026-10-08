package com.mapmory.shared.app

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.remote.AccessTokenProvider
import com.mapmory.shared.data.remote.TripRecordRemoteRepository
import com.mapmory.shared.data.remote.configureCommonHttpClient
import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.data.repository.FakeTripRecordRepository
import com.mapmory.shared.data.settings.MemoryOnboardingPreference
import com.mapmory.shared.data.settings.MemoryThemePreference
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordListUiState
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiState
import com.mapmory.shared.runSuspend
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppContainerTest {
    @Test
    fun `장소_검색과_선택은_초기_인증_실패_후_재시도하면_복구된다`() = runBlocking {
        for (selectPlace in listOf(false, true)) {
            var loginRequests = 0
            var placeRequests = 0
            val client = HttpClient(MockEngine) {
                configureCommonHttpClient()
                engine {
                    addHandler { request ->
                        if (request.url.encodedPath == "/api/v1/auth/login/guest") {
                            loginRequests++
                            if (loginRequests == 1) {
                                throw IllegalStateException("네트워크 연결 실패")
                            }
                            respondJson(
                                """{"data":{"accessToken":"guest-access","refreshToken":"guest-refresh","isNewMember":true}}""",
                            )
                        } else {
                            placeRequests++
                            assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
                            if (selectPlace) {
                                assertEquals("/api/v1/places/test-place", request.url.encodedPath)
                                respondJson(
                                    """{"data":{"placeId":"test-place","name":"도쿄역","countryCode":"JP","manualRegionRequired":false}}""",
                                )
                            } else {
                                assertEquals("/api/v1/places/search", request.url.encodedPath)
                                respondJson("""{"data":[{"placeId":"test-place","name":"도쿄역"}]}""")
                            }
                        }
                    }
                }
            }
            val container = createGuestRemoteAppContainer(
                client = client,
                apiBaseUrl = "https://api.example.com/api/v1",
                tokenStore = TestAuthTokenStore(),
            )
            try {
                // 다른 API에서 인증에 실패한 뒤 장소 기능만으로 복구하는 상황이다.
                assertTrue(container.tagRepository.getTags().isFailure)
                assertEquals(1, loginRequests)
                assertEquals(0, placeRequests)
                val editor = container.viewModelFactory.createTripRecordEditorViewModel()
                if (selectPlace) {
                    editor.selectPlace(
                        PlaceCandidate(
                            "test-place", "도쿄역", null, null, null,
                        ),
                    )
                    assertEquals("test-place", editor.uiState.selectedPlace?.placeId)
                } else {
                    editor.searchPlaces("도쿄역")
                    assertEquals("test-place", editor.uiState.placeSearchResults.single().placeId)
                }
                assertEquals(2, loginRequests)
                assertEquals(1, placeRequests)
            } finally {
                container.close()
            }
        }
    }

    @Test
    fun `게스트_원격_컨테이너는_로그인하고_서버_기록을_저장한_뒤_조회한다`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    requestCount += 1
                    when (requestCount) {
                        1 -> {
                            assertEquals("POST", request.method.value)
                            assertEquals("/api/v1/auth/login/guest", request.url.encodedPath)
                            assertEquals(null, request.headers[HttpHeaders.Authorization])
                            respondJson(
                                """{"data":{"accessToken":"guest-access","refreshToken":"guest-refresh","isNewMember":true}}""",
                            )
                        }

                        2 -> {
                            assertEquals("POST", request.method.value)
                            assertEquals("/api/v1/uploads/presigned-urls", request.url.encodedPath)
                            assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
                            respondJson(
                                """{"data":{"uploads":[{"objectKey":"travel-records/10/server-photo.jpg","presignedUrl":"https://bucket.example.com/server-photo.jpg?signature=test","method":"PUT","contentType":"image/jpeg","expiresIn":300}]}}""",
                            )
                        }

                        3 -> {
                            assertEquals("PUT", request.method.value)
                            assertEquals("bucket.example.com", request.url.host)
                            assertEquals(null, request.headers[HttpHeaders.Authorization])
                            assertEquals(ContentType.Image.JPEG, request.body.contentType)
                            respond(
                                content = ByteReadChannel(""),
                                status = HttpStatusCode.OK,
                            )
                        }

                        4 -> {
                            assertEquals("POST", request.method.value)
                            assertEquals("/api/v1/travel-records", request.url.encodedPath)
                            assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
                            respondJson("""{"data":{"id":101}}""", HttpStatusCode.Created)
                        }

                        5 -> {
                            assertEquals("GET", request.method.value)
                            assertEquals("/api/v1/travel-records/101", request.url.encodedPath)
                            assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
                            respondJson(detailResponse("travel-records/10/server-photo.jpg"))
                        }

                        else -> {
                            assertEquals("GET", request.method.value)
                            assertEquals("/api/v1/travel-records", request.url.encodedPath)
                            assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
                            respondJson(
                                """{"data":{"items":[{"id":101,"title":"제주 서버 여행","regionName":"제주시","startDate":"2026-08-26","endDate":null,"thumbnailUrl":null}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false}}""",
                            )
                        }
                    }
                }
            }
        }
        val tokenStore = TestAuthTokenStore()
        val container = createGuestRemoteAppContainer(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            tokenStore = tokenStore,
        )
        val editor = container.viewModelFactory.createTripRecordEditorViewModel()
        editor.selectLocation(container.regionCatalog.requireByCode("50110"))
        editor.updateTitle("제주 서버 여행")
        editor.updateStartDate("2026-08-26")
        editor.addPhotos(
            listOf(
                SelectedPhoto(
                    id = "content://photo/1",
                    displayName = "jeju-trip.jpg",
                    previewBytes = byteArrayOf(0x0A),
                    originalBytes = byteArrayOf(0x01, 0x02, 0x03),
                ),
            ),
        )

        assertEquals(0L, container.tripRecordRevision.value)
        assertTrue(editor.save())
        assertEquals(1L, container.tripRecordRevision.value)
        val detail = container.viewModelFactory.createTripRecordDetailViewModel()
        detail.load(101)
        val detailState = assertIs<TripRecordDetailUiState.Success>(detail.uiState)
        assertContentEquals(
            byteArrayOf(0x0A),
            detailState.record.photos.single().previewBytes?.bytesForDecoding(),
        )
        val list = container.viewModelFactory.createTripRecordListViewModel()
        list.load()

        val listState = assertIs<TripRecordListUiState.Success>(list.uiState)
        assertEquals(101L, listState.records.single().id)
        assertEquals("제주 서버 여행", listState.records.single().title)
        assertContentEquals(
            byteArrayOf(0x0A),
            listState.records.single().photos.single().previewBytes?.bytesForDecoding(),
        )
        assertEquals(AuthTokens("guest-access", "guest-refresh"), tokenStore.tokens)
        assertEquals(6, requestCount)
        container.close()
    }

    @Test
    fun `상세 조회는 사진 다운로드 없이 URL을 반환한다`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    requestCount += 1
                    when (requestCount) {
                        1 -> respondJson(
                            """{"data":{"accessToken":"guest-access","refreshToken":"guest-refresh","isNewMember":true}}""",
                        )

                        2 -> {
                            assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
                            respondJson(detailResponseWithViewUrl("signature=first"))
                        }

                        else -> {
                            assertEquals("api.example.com", request.url.host)
                            respondJson(detailResponseWithViewUrl("signature=rotated"))
                        }
                    }
                }
            }
        }
        val container = createGuestRemoteAppContainer(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            tokenStore = TestAuthTokenStore(),
        )

        val first = container.tripRecordRepository.getTripRecord(101).getOrThrow()
        val second = container.tripRecordRepository.getTripRecord(101).getOrThrow()

        assertEquals(null, first.media.single().previewBytes)
        assertEquals(null, second.media.single().previewBytes)
        assertEquals(first.media.single().url, second.media.single().url)
        assertEquals(2, requestCount)
        container.close()
    }

    @Test
    fun `토큰_공급자로_원격_컨테이너를_구성할_수_있다`() {
        val container = createRemoteAppContainer(
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        assertIs<com.mapmory.shared.data.repository.PhotoUsageTripRecordRepository>(container.tripRecordRepository)
        assertEquals(null, container.mapSummaryRepository.getCachedRootRegions())
        assertEquals(null, container.tripStatisticsRepository.getCachedStatistics())
        container.close()
    }

    @Test
    fun `교체_가능한_저장소로_컨테이너를_구성할_수_있다`() {
        val repository = FakeTripRecordRepository { "2026-08-24T00:00:00" }
        val themePreference = MemoryThemePreference(initialIsDarkTheme = true)
        val onboardingPreference = MemoryOnboardingPreference(
            initialHasSeenRecordIntroduction = false,
        )

        val container = createAppContainer(
            tripRecordRepository = repository,
            regionCatalog = StaticRegionCatalog(),
            themePreference = themePreference,
            onboardingPreference = onboardingPreference,
        )

        assertIs<com.mapmory.shared.data.repository.PhotoUsageTripRecordRepository>(container.tripRecordRepository)
        assertSame(themePreference, container.themePreference)
        assertSame(onboardingPreference, container.onboardingPreference)
        assertTrue(container.themePreference.loadIsDarkTheme())
        assertTrue(!container.onboardingPreference.hasSeenRecordIntroduction())
        assertEquals(null, container.mapSummaryRepository.getCachedRootRegions())
    }

    @Test
    fun `화면_ViewModel은_UI_상태를_공유하지_않고_저장소를_공유한다`() = runSuspend {
        val container = createInMemoryAppContainer()
        val gangnam = container.regionCatalog.requireByCode("11680")
        val editor = container.viewModelFactory.createTripRecordEditorViewModel()

        editor.selectLocation(gangnam)
        editor.updateTitle("컨테이너 여행")
        editor.updateContent("화면별 ViewModel이 같은 저장소를 바라본다.")
        editor.updateStartDate("2026-08-24")
        editor.addPhotos(
            listOf(
                SelectedPhoto(
                    id = "local/photo.jpg",
                    displayName = "photo.jpg",
                    previewBytes = byteArrayOf(1, 2, 3),
                    originalBytes = byteArrayOf(4, 5, 6),
                ),
            ),
        )

        val initialStatistics = container.viewModelFactory.createTripStatisticsViewModel()
        initialStatistics.refresh()
        assertTrue(container.tripStatisticsRepository.getCachedStatistics() != null)

        assertTrue(editor.save())
        assertEquals(null, container.tripStatisticsRepository.getCachedStatistics())

        val list = container.viewModelFactory.createTripRecordListViewModel()
        list.load()
        val listState = assertIs<TripRecordListUiState.Success>(list.uiState)
        val savedRecord = listState.records.single()
        assertEquals("컨테이너 여행", savedRecord.title)
        assertEquals("강남구", savedRecord.locationName)
        assertContentEquals(
            byteArrayOf(1, 2, 3),
            savedRecord.photos.single().previewBytes?.bytesForDecoding(),
        )
        assertEquals(null, savedRecord.photos.single().originalBytes)

        val detail = container.viewModelFactory.createTripRecordDetailViewModel()
        detail.load(savedRecord.id)
        val detailState = assertIs<TripRecordDetailUiState.Success>(detail.uiState)
        assertEquals(savedRecord.id, detailState.record.id)
        assertEquals("강남구", detailState.record.locationName)
        assertContentEquals(
            byteArrayOf(1, 2, 3),
            detailState.record.photos.single().previewBytes?.bytesForDecoding(),
        )

        val statistics = container.viewModelFactory.createTripStatisticsViewModel()
        statistics.refresh()
        val statisticsState = assertIs<TripStatisticsUiState.Success>(statistics.uiState).statistics
        assertEquals(1, statisticsState.recordCount)
        assertEquals(1, statisticsState.photoCount)
        assertEquals(1, statisticsState.koreaVisitedCount)

        assertNotSame(list, container.viewModelFactory.createTripRecordListViewModel())
        assertNotSame(detail, container.viewModelFactory.createTripRecordDetailViewModel())
        assertNotSame(
            container.viewModelFactory.createTripStatisticsViewModel(),
            container.viewModelFactory.createTripStatisticsViewModel(),
        )
    }
}

private class TestAuthTokenStore : AuthTokenStore {
    var tokens: AuthTokens? = null
        private set

    override fun load(): AuthTokens? = tokens

    override fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = ByteReadChannel(json),
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

private fun detailResponse(objectKey: String): String =
    """{"data":{"id":101,"title":"제주 서버 여행","content":"","region":{"country":{"code":"KR","name":"대한민국"},"province":{"code":"49","name":"제주특별자치도"},"district":{"code":"50110","name":"제주시"}},"startDate":"2026-08-26","endDate":null,"objectKeys":["$objectKey"],"createdAt":"2026-08-26T13:00:00","updatedAt":"2026-08-26T13:00:00"}}"""

private fun detailResponseWithViewUrl(signature: String): String =
    """{"data":{"id":101,"title":"제주 서버 여행","content":"","region":{"country":{"code":"KR","name":"대한민국"},"province":{"code":"49","name":"제주특별자치도"},"district":{"code":"50110","name":"제주시"}},"startDate":"2026-08-26","endDate":null,"objectKeys":["travel-records/10/photo.jpg"],"media":[{"id":55,"objectKey":"travel-records/10/photo.jpg","viewUrl":"https://bucket.example.com/photo.jpg?$signature","viewUrlExpiresIn":300,"sortOrder":0}],"createdAt":"2026-08-26T13:00:00","updatedAt":"2026-08-26T13:00:00"}}"""
