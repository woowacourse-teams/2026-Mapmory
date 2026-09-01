package com.mapmory.shared.data.remote

import com.mapmory.shared.data.local.StaticRegionCatalog
import com.mapmory.shared.domain.model.TripRecordDraft
import com.mapmory.shared.domain.model.TripRecordQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TripRecordRemoteRepositoryTest {
    private val catalog = StaticRegionCatalog()

    @Test
    fun `기록_조회는_Bearer_토큰과_지역_코드_경로를_전송한다`() = runBlocking {
        val jejuProvince = catalog.requireByCode("KR-49")
        val client = client { request ->
            assertEquals("GET", request.method.value)
            assertEquals("Bearer guest-access-token", request.headers[HttpHeaders.Authorization])
            assertEquals("KR", request.url.parameters["countryCode"])
            assertEquals("49", request.url.parameters["provinceCode"])
            assertEquals(null, request.url.parameters["districtCode"])
            assertEquals("0", request.url.parameters["page"])
            assertEquals("20", request.url.parameters["size"])
            jsonResponse(
                """{"data":{"items":[{"id":101,"title":"제주 여행","regionName":"제주특별자치도","startDate":"2026-08-11","endDate":null,"thumbnailUrl":"https://bucket.example.com/photo.jpg?signature=fresh","thumbnailUrlExpiresIn":300}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false}}""",
            )
        }

        val page = repository(client).getTripRecords(
            TripRecordQuery(locationId = jejuProvince.id),
        ).getOrThrow()

        assertEquals("제주특별자치도", page.records.single().regionName)
        assertEquals(
            "https://bucket.example.com/photo.jpg?signature=fresh",
            page.records.single().thumbnailUrl,
        )
        assertEquals(300L, page.records.single().thumbnailUrlExpiresIn)
        client.close()
    }

    @Test
    fun `기록_생성은_코드_기반_본문을_전송하고_생성된_상세를_조회한다`() = runBlocking {
        val jejuCity = catalog.requireByCode("50110")
        var requestCount = 0
        val client = client { request ->
            requestCount += 1
            assertEquals("Bearer guest-access-token", request.headers[HttpHeaders.Authorization])
            when (requestCount) {
                1 -> {
                    assertEquals("POST", request.method.value)
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    jsonResponse("""{"data":{"id":101}}""", HttpStatusCode.Created)
                }

                else -> {
                    assertEquals("GET", request.method.value)
                    assertEquals("/api/v1/travel-records/101", request.url.encodedPath)
                    jsonResponse(detailResponse(title = "비 오는 날의 제주시"))
                }
            }
        }

        val created = repository(client).createTripRecord(
            TripRecordDraft(
                locationId = jejuCity.id,
                title = "비 오는 날의 제주시",
                content = "골목을 걸었다.",
                startDate = "2026-08-11",
                endDate = null,
                mediaObjectKeys = listOf("travel-records/guest/a.jpg"),
            ),
        ).getOrThrow()

        assertEquals(2, requestCount)
        assertEquals(jejuCity.id, created.locationId)
        assertEquals("travel-records/guest/a.jpg", created.media.single().objectKey)
        client.close()
    }

    @Test
    fun `기록_수정은_추가_GET_없이_PUT_응답_상세를_사용한다`() = runBlocking {
        val jejuCity = catalog.requireByCode("50110")
        var requestCount = 0
        val client = client { request ->
            requestCount += 1
            assertEquals("PUT", request.method.value)
            jsonResponse(detailResponse(title = "수정된 제주 여행"))
        }

        val updated = repository(client).updateTripRecord(
            id = 101,
            draft = TripRecordDraft(
                locationId = jejuCity.id,
                title = "수정된 제주 여행",
                content = "수정한 내용",
                startDate = "2026-08-11",
                endDate = null,
                mediaObjectKeys = emptyList(),
            ),
        ).getOrThrow()

        assertEquals(1, requestCount)
        assertEquals("수정된 제주 여행", updated.title)
        client.close()
    }

    @Test
    fun `Problem_Details_응답을_API_예외로_노출한다`() = runBlocking {
        val client = client {
            jsonResponse(
                json = """{"title":"요청 값이 올바르지 않습니다.","status":400,"detail":"1개의 필드가 유효하지 않습니다.","instance":"/api/v1/travel-records","code":"VALIDATION_ERROR","errors":[{"field":"countryCode","detail":"형식이 올바르지 않습니다."}]}""",
                status = HttpStatusCode.BadRequest,
                contentType = ContentType.parse("application/problem+json"),
            )
        }

        val error = repository(client).getTripRecords().exceptionOrNull()

        val apiError = assertIs<MapmoryApiException>(error)
        assertEquals(400, apiError.statusCode)
        assertEquals("VALIDATION_ERROR", apiError.code)
        assertEquals("countryCode", apiError.errors.single().field)
        assertTrue(apiError.message.orEmpty().contains("1개의 필드"))
        client.close()
    }

    @Test
    fun `토큰이_없으면_보호_요청을_전송하기_전에_실패한다`() = runBlocking {
        var requestCount = 0
        val client = client {
            requestCount += 1
            error("토큰이 없으면 네트워크 요청을 보내면 안 됩니다.")
        }
        val repository = TripRecordRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { null },
            regionCatalog = catalog,
        )

        val error = repository.getTripRecords().exceptionOrNull()

        assertIs<MissingAccessTokenException>(error)
        assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun `잘못된_페이지와_기록_ID는_네트워크_호출_전에_실패한다`() = runBlocking {
        var requestCount = 0
        val client = client {
            requestCount += 1
            error("명세에 어긋난 값은 네트워크 요청 전에 거절해야 합니다.")
        }
        val repository = repository(client)

        assertIs<IllegalArgumentException>(
            repository.getTripRecords(TripRecordQuery(page = -1)).exceptionOrNull(),
        )
        assertIs<IllegalArgumentException>(
            repository.getTripRecords(TripRecordQuery(size = 101)).exceptionOrNull(),
        )
        assertIs<IllegalArgumentException>(repository.getTripRecord(0).exceptionOrNull())
        assertIs<IllegalArgumentException>(repository.deleteTripRecord(-1).exceptionOrNull())
        assertEquals(0, requestCount)
        client.close()
    }

    @Test
    fun `기록_삭제는_보호_엔드포인트의_No_Content를_허용한다`() = runBlocking {
        val client = client { request ->
            assertEquals("DELETE", request.method.value)
            assertEquals("/api/v1/travel-records/101", request.url.encodedPath)
            assertEquals("Bearer guest-access-token", request.headers[HttpHeaders.Authorization])
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent,
            )
        }

        val result = repository(client).deleteTripRecord(101)

        assertTrue(result.isSuccess)
        client.close()
    }

    private fun repository(client: HttpClient) = TripRecordRemoteRepository(
        client = client,
        apiBaseUrl = "https://api.example.com/api/v1",
        accessTokenProvider = AccessTokenProvider { "guest-access-token" },
        regionCatalog = catalog,
    )

    private fun client(
        handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) ->
            io.ktor.client.request.HttpResponseData,
    ): HttpClient = HttpClient(MockEngine) {
        configureCommonHttpClient()
        engine { addHandler(handler) }
    }

    private fun MockRequestHandleScope.jsonResponse(
        json: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: ContentType = ContentType.Application.Json,
    ) = respond(
        content = ByteReadChannel(json),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, contentType.toString()),
    )

    private fun detailResponse(title: String): String =
        """{"data":{"id":101,"title":"$title","content":"골목을 걸었다.","region":{"country":{"code":"KR","name":"대한민국"},"province":{"code":"49","name":"제주특별자치도"},"district":{"code":"50110","name":"제주시"}},"startDate":"2026-08-11","endDate":null,"objectKeys":["travel-records/guest/a.jpg"],"createdAt":"2026-08-14T10:30:00","updatedAt":"2026-08-15T09:00:00"}}"""
}
