package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.AuthGateway
import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.auth.GuestSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AlbumRemoteRepositoryTest {
    @Test fun albumWritesAndReadsFollowCurrentContract() = runBlocking {
        val entry = """{"travelRecordId":7,"owner":{"memberId":9,"nickname":"나","me":true},"title":null,"content":null,"startDate":"2026-10-01","endDate":null,"placeName":null,"photos":[{"id":12,"viewUrl":"https://example.com/original.jpg","viewUrlExpiresIn":300,"thumbnailUrl":"https://example.com/thumb.webp","thumbnailUrlExpiresIn":180,"sortOrder":0}],"addedAt":"2026-10-01T12:00:00"}"""
        val detail = """{"id":4,"groupId":3,"name":"여행","region":{"id":1,"code":"11","type":"CITY","name":"서울"},"startDate":"2026-10-01","endDate":null,"opener":{"memberId":9,"nickname":"나","me":true},"myTravelRecordIds":[7,8],"entries":[$entry]}"""
        val legacyDetail = detail.replace(",\"thumbnailUrl\":\"https://example.com/thumb.webp\",\"thumbnailUrlExpiresIn\":180", "")
        var calls = 0
        val client = HttpClient(MockEngine { req ->
            assertEquals("Bearer guest-access", req.headers[HttpHeaders.Authorization])
            val json = when (calls++) {
                0 -> { assertEquals("/api/v1/groups/3/albums", req.url.encodedPath); assertEquals("1", req.url.parameters["page"]); """{"data":{"items":[],"page":1,"size":20,"totalElements":0,"totalPages":0,"hasNext":false}}""" }
                1 -> { assertEquals("/api/v1/groups/3/albums/4", req.url.encodedPath); """{"data":$detail}""" }
                2 -> { assertEquals("POST", req.method.value); assertTrue(assertIs<TextContent>(req.body).text.contains("\"travelRecordId\":7")); """{"data":$legacyDetail}""" }
                3 -> { assertEquals("/api/v1/groups/3/albums/4/entries", req.url.encodedPath); assertTrue(assertIs<TextContent>(req.body).text.contains("\"travelRecordId\":8")); """{"data":$detail}""" }
                else -> { assertEquals("PATCH", req.method.value); """{"data":$detail}""" }
            }
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { configureCommonHttpClient() }
        val store = object : AuthTokenStore {
            private var tokens: AuthTokens? = null
            override fun load() = tokens
            override fun save(tokens: AuthTokens) { this.tokens = tokens }
            override fun clear() { tokens = null }
        }
        val session = GuestSessionManager(object : AuthGateway {
            override suspend fun loginAsGuest() = Result.success(AuthTokens("guest-access", "refresh"))
            override suspend fun refresh(refreshToken: String) = Result.success(AuthTokens("access", "refresh"))
        }, store)
        try {
            val repo = AlbumRemoteRepository(client, "https://example.com/api/v1", session)
            assertEquals(1, repo.list(3, 1).getOrThrow().page)
            val album = repo.detail(3, 4).getOrThrow()
            val photo = album.entries.single().photos.single()
            assertEquals(listOf(7L, 8L), album.myTravelRecordIds)
            assertEquals("https://example.com/thumb.webp", photo.thumbnailUrl)
            assertEquals(180L, photo.thumbnailUrlExpiresIn)
            val legacyPhoto = repo.create(3, 7).getOrThrow().entries.single().photos.single()
            assertNull(legacyPhoto.thumbnailUrl)
            assertNull(legacyPhoto.thumbnailUrlExpiresIn)
            assertEquals(4, repo.addRecord(3, 4, 8).getOrThrow().id)
            assertEquals(4, repo.update(3, 4, name = "제목").getOrThrow().id)
            assertEquals(5, calls)
        } finally { session.close(); client.close() }
    }
}
