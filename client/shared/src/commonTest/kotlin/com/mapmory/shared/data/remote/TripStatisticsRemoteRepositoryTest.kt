package com.mapmory.shared.data.remote

import com.mapmory.shared.domain.model.MapRegionType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class TripStatisticsRemoteRepositoryTest {
    @Test
    fun `전용_통계_API를_Bearer_토큰으로_조회해_도메인으로_변환한다`() = runBlocking {
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    assertEquals(
                        "/api/v1/travel-records/statistics",
                        request.url.encodedPath,
                    )
                    assertEquals("Bearer guest-token", request.headers[HttpHeaders.Authorization])
                    respond(
                        content = ByteReadChannel(
                            """{"data":{"recordCount":24,"mediaCount":138,"visitedCountryCount":3,"visitedKoreaDistrictCount":8,"visitedCountryCodes":["JP","KR","US"],"topRegions":[{"regionId":10,"code":"11","regionType":"PROVINCE","name":"서울특별시","recordCount":7},{"regionId":2,"code":"JP","regionType":"COUNTRY","name":"일본","recordCount":4}]}}""",
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString(),
                        ),
                    )
                }
            }
        }
        val repository = TripStatisticsRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        val statistics = repository.getStatistics().getOrThrow()

        assertEquals(24L, statistics.recordCount)
        assertEquals(138L, statistics.mediaCount)
        assertEquals(8L, statistics.visitedKoreaDistrictCount)
        assertEquals(listOf("JP", "KR", "US"), statistics.visitedCountryCodes)
        assertEquals(MapRegionType.PROVINCE, statistics.topRegions.first().type)
        assertEquals("일본", statistics.topRegions.last().name)
        client.close()
    }
}
