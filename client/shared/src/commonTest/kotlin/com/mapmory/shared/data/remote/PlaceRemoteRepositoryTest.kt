package com.mapmory.shared.data.remote

import com.mapmory.shared.domain.model.PlaceCandidate
import com.mapmory.shared.domain.model.PlaceReference
import com.mapmory.shared.domain.model.PlaceRegionSuggestion
import com.mapmory.shared.domain.model.PlaceSelection
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

class PlaceRemoteRepositoryTest {
    @Test
    fun `장소를_검색하고_선택하면_행정구역_추천을_받는다`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    requestCount += 1
                    assertEquals("Bearer guest-token", request.headers[HttpHeaders.Authorization])
                    when (requestCount) {
                        1 -> {
                            assertEquals("/api/v1/places/search", request.url.encodedPath)
                            assertEquals("판교", request.url.parameters["query"])
                            assertEquals("session-1", request.url.parameters["sessionToken"])
                            respondJson(
                                """{"data":[{"placeId":"geoapify-place-id","name":"판교역","address":"경기도 성남시","attribution":"© OpenStreetMap contributors","attributionUrl":"https://www.openstreetmap.org/copyright"}]}""",
                            )
                        }

                        else -> {
                            assertEquals("/api/v1/places/geoapify-place-id", request.url.encodedPath)
                            assertEquals("session-1", request.url.parameters["sessionToken"])
                            respondJson(
                                """{"data":{"placeId":"geoapify-place-id","name":"판교역","countryCode":"KR","suggestedRegion":{"country":{"code":"KR","name":"대한민국"},"province":{"code":"41","name":"경기도"},"district":{"code":"41135","name":"성남시 분당구"}},"manualRegionRequired":false,"attribution":"© OpenStreetMap contributors","attributionUrl":"https://www.openstreetmap.org/copyright"}}""",
                            )
                        }
                    }
                }
            }
        }
        val repository = PlaceRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        assertEquals(
            PlaceCandidate(
                placeId = "geoapify-place-id",
                name = "판교역",
                address = "경기도 성남시",
                attribution = "© OpenStreetMap contributors",
                attributionUrl = "https://www.openstreetmap.org/copyright",
            ),
            repository.searchPlaces(" 판교 ", "session-1").getOrThrow().single(),
        )
        assertEquals(
            PlaceSelection(
                place = PlaceReference(
                    placeId = "geoapify-place-id",
                    name = "판교역",
                    attribution = "© OpenStreetMap contributors",
                    attributionUrl = "https://www.openstreetmap.org/copyright",
                    countryCode = "KR",
                ),
                countryCode = "KR",
                suggestedRegion = PlaceRegionSuggestion("KR", "41", "41135"),
                manualRegionRequired = false,
            ),
            repository.selectPlace("geoapify-place-id", "session-1").getOrThrow(),
        )
        assertEquals(2, requestCount)
        client.close()
    }

    @Test
    fun `Google_장소는_한_글자로_검색하고_이름과_국가가_없는_선택_응답도_받는다`() = runBlocking {
        var requestCount = 0
        val client = HttpClient(MockEngine) {
            configureCommonHttpClient()
            engine {
                addHandler { request ->
                    requestCount += 1
                    when (requestCount) {
                        1 -> {
                            assertEquals("괌", request.url.parameters["query"])
                            assertEquals("google-session", request.url.parameters["sessionToken"])
                            respondJson(
                                """{"data":[{"placeId":"ChIJ-guam","name":"괌","address":"미국","attribution":"Google Maps","attributionUrl":"https://www.google.com/maps"}]}""",
                            )
                        }

                        else -> {
                            assertEquals("/api/v1/places/ChIJ-guam", request.url.encodedPath)
                            assertEquals("google-session", request.url.parameters["sessionToken"])
                            respondJson(
                                """{"data":{"placeId":"ChIJ-guam","name":null,"countryCode":null,"suggestedRegion":null,"manualRegionRequired":true,"attribution":"Google Maps","attributionUrl":"https://www.google.com/maps"}}""",
                            )
                        }
                    }
                }
            }
        }
        val repository = PlaceRemoteRepository(
            client = client,
            apiBaseUrl = "https://api.example.com/api/v1",
            accessTokenProvider = AccessTokenProvider { "guest-token" },
        )

        assertEquals("ChIJ-guam", repository.searchPlaces("괌", "google-session").getOrThrow().single().placeId)
        assertEquals(
            PlaceSelection(
                place = PlaceReference(
                    placeId = "ChIJ-guam",
                    name = null,
                    attribution = "Google Maps",
                    attributionUrl = "https://www.google.com/maps",
                ),
                countryCode = null,
                suggestedRegion = null,
                manualRegionRequired = true,
            ),
            repository.selectPlace("ChIJ-guam", "google-session").getOrThrow(),
        )
        assertEquals(2, requestCount)
        client.close()
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(json: String) = respond(
        content = ByteReadChannel(json),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
