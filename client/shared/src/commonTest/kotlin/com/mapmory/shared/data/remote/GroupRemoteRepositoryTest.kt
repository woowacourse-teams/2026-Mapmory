package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.AuthGateway
import com.mapmory.shared.data.auth.AuthTokenStore
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.domain.group.GroupDetail
import com.mapmory.shared.domain.group.GroupMemberView
import com.mapmory.shared.domain.group.GroupSummary
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupRemoteRepositoryTest {
    @Test fun leaveAcceptsEmpty204AndRefreshesExpiredToken() = runBlocking {
        var calls = 0
        withRepository({ request ->
            assertEquals("DELETE", request.method.value)
            assertEquals("/api/v1/groups/3/members/me", request.url.encodedPath)
            if (calls++ == 0) {
                jsonResponse("""{"status":401,"code":"EXPIRED_ACCESS_TOKEN"}""", HttpStatusCode.Unauthorized)
            } else {
                assertEquals("Bearer rotated-access", request.headers[HttpHeaders.Authorization])
                respond("", HttpStatusCode.NoContent)
            }
        }) { repository, _ ->
            assertTrue(repository.leaveGroup(3).isSuccess)
            assertEquals(2, calls)
        }
    }

    @Test fun failedLeaveDoesNotTreatAny404AsSuccessOrAutomaticallyRetry() = runBlocking {
        var calls = 0
        withRepository({ calls++; jsonResponse("""{"status":404,"code":"GROUP_NOT_FOUND"}""", HttpStatusCode.NotFound) }) { repository, _ ->
            val failure = assertIs<MapmoryApiException>(repository.leaveGroup(3).exceptionOrNull())
            assertEquals("GROUP_NOT_FOUND", failure.code)
            assertEquals(1, calls)
        }
    }

    @Test
    fun groupEndpointsFollowServerContract() = runBlocking {
        var calls = 0
        val expected = listOf(
            "GET" to "/api/v1/groups",
            "POST" to "/api/v1/groups",
            "GET" to "/api/v1/groups/3",
            "PATCH" to "/api/v1/groups/3",
        )
        withRepository({ request ->
            assertEquals(expected[calls], request.method.value to request.url.encodedPath)
            assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
            when (calls++) {
                0 -> jsonResponse("""{"data":[{"id":3,"name":"제주 가족","memberCount":2,"albumCount":0}]}""")
                1, 3 -> {
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    assertEquals("""{"name":"제주 가족"}""", assertIs<TextContent>(request.body).text)
                    jsonResponse(GroupJson, if (calls == 2) HttpStatusCode.Created else HttpStatusCode.OK)
                }
                else -> jsonResponse(GroupJson)
            }
        }) { repository, _ ->
            assertEquals(listOf(GroupSummary(3, "제주 가족", 2, 0)), repository.groups().getOrThrow())
            assertEquals(ExpectedGroup, repository.createGroup("제주 가족").getOrThrow())
            assertEquals(ExpectedGroup, repository.getGroup(3).getOrThrow())
            assertEquals(ExpectedGroup, repository.renameGroup(3, "제주 가족").getOrThrow())
            assertEquals(4, calls)
        }
    }

    @Test
    fun invitationEndpointsFollowServerContract() = runBlocking {
        var calls = 0
        withRepository({ request ->
            assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
            when (calls++) {
                0 -> {
                    assertEquals("POST", request.method.value)
                    assertEquals("/api/v1/groups/3/invitations", request.url.encodedPath)
                    jsonResponse("""{"data":{"token":"link-token","code":"4KD7PZ","expiresAt":"2026-10-28T18:00:00"}}""", HttpStatusCode.Created)
                }
                1 -> {
                    assertEquals("GET", request.method.value)
                    assertEquals("/api/v1/invitations/4kd7pz", request.url.encodedPath)
                    jsonResponse("""{"data":{"groupName":"제주 가족","memberCount":2,"alreadyMember":false}}""")
                }
                else -> {
                    assertEquals("POST", request.method.value)
                    assertEquals("/api/v1/invitations/link-token/accept", request.url.encodedPath)
                    jsonResponse(GroupJson)
                }
            }
        }) { repository, _ ->
            val issued = repository.createInvitation(3).getOrThrow()
            assertEquals("link-token", issued.token)
            assertEquals("4KD7PZ", issued.code)
            assertEquals(LocalDateTime(2026, 10, 28, 18, 0), issued.expiresAt)
            assertFalse(issued.toString().contains(issued.token))
            assertFalse(issued.toString().contains(issued.code))
            val preview = repository.previewInvitation("4kd7pz").getOrThrow()
            assertEquals("제주 가족", preview.groupName)
            assertEquals(2, preview.memberCount)
            assertFalse(preview.alreadyMember)
            // The repository preserves the server's successful idempotent acceptance response.
            assertEquals(ExpectedGroup, repository.acceptInvitation("link-token").getOrThrow())
            assertEquals(3, calls)
        }
    }

    @Test
    fun invitationInputCannotChangeRequestPathOrQuery() = runBlocking {
        withRepository({ request ->
            assertEquals("/api/v1/invitations/a%2Fb%3Fx%23y", request.url.encodedPath)
            assertTrue(request.url.parameters.isEmpty())
            jsonResponse("""{"data":{"groupName":"제주 가족","memberCount":2,"alreadyMember":true}}""")
        }) { repository, _ ->
            assertTrue(repository.previewInvitation("a/b?x#y").getOrThrow().alreadyMember)
        }
    }

    @Test
    fun serverBusinessErrorsArePreservedWithoutRetryingWrites() = runBlocking {
        val failures = listOf(
            HttpStatusCode.BadRequest to "INVALID_GROUP_NAME",
            HttpStatusCode.Forbidden to "SOCIAL_LOGIN_REQUIRED",
            HttpStatusCode.NotFound to "GROUP_NOT_FOUND",
            HttpStatusCode.NotFound to "INVITATION_NOT_FOUND",
            HttpStatusCode.Conflict to "GROUP_FULL",
            HttpStatusCode.Conflict to "TOO_MANY_GROUPS",
            HttpStatusCode.TooManyRequests to "INVITATION_RATE_LIMITED",
            HttpStatusCode.InternalServerError to "SERVER_ERROR",
        )
        for ((status, code) in failures) {
            var calls = 0
            withRepository({
                calls++
                jsonResponse("""{"status":${status.value},"code":"$code","detail":"요청 실패"}""", status)
            }) { repository, _ ->
                val error = assertIs<MapmoryApiException>(repository.acceptInvitation("4KD7PZ").exceptionOrNull())
                assertEquals(status.value, error.statusCode)
                assertEquals(code, error.code)
                assertEquals(1, calls)
            }
        }
    }

    @Test
    fun expiredAccessTokenRefreshesBeforeRetryingGroupRequest() = runBlocking {
        var calls = 0
        withRepository({ request ->
            calls++
            if (calls == 1) {
                assertEquals("Bearer guest-access", request.headers[HttpHeaders.Authorization])
                jsonResponse("""{"status":401,"code":"EXPIRED_ACCESS_TOKEN"}""", HttpStatusCode.Unauthorized)
            } else {
                assertEquals("Bearer rotated-access", request.headers[HttpHeaders.Authorization])
                jsonResponse(GroupJson)
            }
        }) { repository, _ ->
            assertEquals(ExpectedGroup, repository.getGroup(3).getOrThrow())
            assertEquals(2, calls)
        }
    }

    @Test
    fun invalidSessionPreventsFurtherGroupWrites() = runBlocking {
        var calls = 0
        withRepository({
            calls++
            jsonResponse("""{"status":401,"code":"INACTIVE_MEMBER"}""", HttpStatusCode.Unauthorized)
        }) { repository, session ->
            val error = assertIs<MapmoryApiException>(repository.createGroup("그룹").exceptionOrNull())
            assertEquals("INACTIVE_MEMBER", error.code)
            assertTrue(session.state.value.recoveryRequired)
            assertTrue(repository.createInvitation(3).isFailure)
            assertEquals(1, calls)
        }
    }

    @Test
    fun malformedResponsesFailInsteadOfInventingGroupData() = runBlocking {
        for (response in listOf("""{"data":{"id":3,"name":"그룹"}}""", """{"data":null}""")) {
            withRepository({ jsonResponse(response) }) { repository, _ ->
                assertTrue(repository.getGroup(3).isFailure)
            }
        }
        withRepository({ jsonResponse("""{"data":{"token":"token","code":"4KD7PZ","expiresAt":"invalid"}}""") }) { repository, _ ->
            assertTrue(repository.createInvitation(3).isFailure)
        }
    }

    @Test
    fun cancellationIsNotConvertedToRepositoryFailure() = runBlocking {
        withRepository({ throw CancellationException("cancelled") }) { repository, _ ->
            assertFailsWith<CancellationException> { repository.groups() }
        }
    }

    private suspend fun withRepository(
        handler: MockRequestHandler,
        block: suspend (GroupRemoteRepository, GuestSessionManager) -> Unit,
    ) {
        val client = HttpClient(MockEngine(handler)) { configureCommonHttpClient() }
        val gateway = object : AuthGateway {
            override suspend fun loginAsGuest() = Result.success(AuthTokens("guest-access", "refresh"))
            override suspend fun refresh(refreshToken: String) = Result.success(AuthTokens("rotated-access", "rotated-refresh"))
        }
        val store = object : AuthTokenStore {
            private var tokens: AuthTokens? = null
            override fun load() = tokens
            override fun save(tokens: AuthTokens) { this.tokens = tokens }
            override fun clear() { tokens = null }
        }
        val session = GuestSessionManager(gateway, store)
        client.installMapmoryAuthRetry(session, "https://example.com/api/v1")
        try {
            block(GroupRemoteRepository(client, "https://example.com/api/v1/", session), session)
        } finally {
            session.close()
            client.close()
        }
    }

    private fun MockRequestHandleScope.jsonResponse(json: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(json, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
}

private const val GroupJson = """{"data":{"id":3,"name":"제주 가족","members":[{"nickname":"지수","owner":true,"me":true},{"nickname":"민호","owner":false,"me":false}]}}"""
private val ExpectedGroup = GroupDetail(3, "제주 가족", listOf(GroupMemberView("지수", true, true), GroupMemberView("민호", false, false)))
