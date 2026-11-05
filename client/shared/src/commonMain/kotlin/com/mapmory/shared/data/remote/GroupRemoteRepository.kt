package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.GroupDetailDto
import com.mapmory.shared.data.remote.model.GroupInvitationDto
import com.mapmory.shared.data.remote.model.GroupInvitationPreviewDto
import com.mapmory.shared.data.remote.model.GroupNameRequestDto
import com.mapmory.shared.data.remote.model.GroupSummaryDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.domain.group.GroupDetail
import com.mapmory.shared.domain.group.GroupInvitationPreview
import com.mapmory.shared.domain.group.GroupSummary
import com.mapmory.shared.domain.group.IssuedGroupInvitation
import com.mapmory.shared.domain.repository.GroupRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType

internal class GroupRemoteRepository(
    private val client: HttpClient,
    private val apiBaseUrl: String,
    private val session: GuestSessionManager,
) : GroupRepository {
    override suspend fun groups(): Result<List<GroupSummary>> = authenticated {
        request<List<GroupSummaryDto>>(HttpMethod.Get, "groups").map { it.toDomain() }
    }

    override suspend fun createGroup(name: String): Result<GroupDetail> = authenticated {
        request<GroupDetailDto>(HttpMethod.Post, "groups") { groupName(name) }.toDomain()
    }

    override suspend fun getGroup(groupId: Long): Result<GroupDetail> = authenticated {
        request<GroupDetailDto>(HttpMethod.Get, "groups", groupId.toString()).toDomain()
    }

    override suspend fun renameGroup(groupId: Long, name: String): Result<GroupDetail> = authenticated {
        request<GroupDetailDto>(HttpMethod.Patch, "groups", groupId.toString()) { groupName(name) }.toDomain()
    }

    override suspend fun createInvitation(groupId: Long): Result<IssuedGroupInvitation> = authenticated {
        request<GroupInvitationDto>(HttpMethod.Post, "groups", groupId.toString(), "invitations").toDomain()
    }

    override suspend fun leaveGroup(groupId: Long): Result<Unit> = authenticated {
        client.request(apiBaseUrl.trimEnd('/')) {
            method = HttpMethod.Delete
            url { appendPathSegments("groups", groupId.toString(), "members", "me") }
            bearerAuth(checkNotNull(session.getAccessToken()))
            timeout { requestTimeoutMillis = 20_000 }
        }.requireSuccess()
        Unit // The server returns 204 without an ApiResponse body.
    }

    override suspend fun previewInvitation(tokenOrCode: String): Result<GroupInvitationPreview> = authenticated {
        request<GroupInvitationPreviewDto>(HttpMethod.Get, "invitations", tokenOrCode).toDomain()
    }

    override suspend fun acceptInvitation(tokenOrCode: String): Result<GroupDetail> = authenticated {
        request<GroupDetailDto>(HttpMethod.Post, "invitations", tokenOrCode, "accept").toDomain()
    }

    private suspend fun <T> authenticated(block: suspend () -> T): Result<T> =
        session.withAuthenticatedSession { apiCall { block() } }

    private suspend inline fun <reified T> request(
        method: HttpMethod,
        vararg path: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {},
    ): T = client.request(apiBaseUrl.trimEnd('/')) {
        timeout { requestTimeoutMillis = 20_000 }
        this.method = method
        url { appendPathSegments(*path, encodeSlash = true) }
        bearerAuth(checkNotNull(session.getAccessToken()))
        configure()
    }.requireSuccess().body<ApiResponseDto<T>>().data

    private fun HttpRequestBuilder.groupName(name: String) {
        contentType(ContentType.Application.Json)
        setBody(GroupNameRequestDto(name))
    }
}
