package com.mapmory.shared.data.remote

import com.mapmory.shared.domain.model.MapRegionLevel
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

class MapSummaryRemoteRepositoryTest {
    @Test
    fun rootsAndChildrenUseSummaryEndpointsAndBearerToken() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    requestCount += 1
                    assertEquals("Bearer guest-token", request.headers[HttpHeaders.Authorization])
                    assertEquals("7", request.url.parameters["tagId"])
                    val expectedPath = if (requestCount == 1) {
                        "/api/v1/travel-records/map-summary/regions/roots"
                    } else {
                        "/api/v1/travel-records/map-summary/regions/1/children"
                    }
                    assertEquals(expectedPath, request.url.encodedPath)
                    respond(
                        content = ByteReadChannel(
                            if (requestCount == 1) {
                                """{"data":[{"regionId":1,"code":"KR","regionType":"COUNTRY","name":"대한민국","count":12,"level":"HIGH"}]}"""
                            } else {
                                """{"data":[{"regionId":15,"code":"49","regionType":"PROVINCE","name":"제주특별자치도","count":5,"level":"MEDIUM"}]}"""
                            },
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        val repository = MapSummaryRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        val root = repository.getRootRegions(tagId = 7).getOrThrow().single()
        val province = repository.getChildRegions(root.regionId, tagId = 7).getOrThrow().single()

        assertEquals(MapRegionType.COUNTRY, root.type)
        assertEquals(MapRegionLevel.HIGH, root.level)
        assertEquals("49", province.code)
        assertEquals(MapRegionLevel.MEDIUM, province.level)
        client.close()
    }
}
