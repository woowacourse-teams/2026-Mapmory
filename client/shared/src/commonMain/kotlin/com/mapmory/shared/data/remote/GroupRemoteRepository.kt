package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.GuestSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import com.mapmory.shared.data.remote.model.ApiResponseDto
import kotlinx.serialization.Serializable

@Serializable
data class RemoteGroupSummary(val id: Long, val name: String, val memberCount: Long, val albumCount: Long = 0)

interface GroupRepository {
    suspend fun groups(): Result<List<RemoteGroupSummary>>
}

internal class GroupRemoteRepository(
    private val client: HttpClient,
    private val apiBaseUrl: String,
    private val session: GuestSessionManager,
) : GroupRepository {
    override suspend fun groups(): Result<List<RemoteGroupSummary>> = session.withAuthenticatedSession {
        apiCall {
            client.get("${apiBaseUrl.trimEnd('/')}/groups") {
                bearerAuth(checkNotNull(session.getAccessToken()))
            }.requireSuccess().body<ApiResponseDto<List<RemoteGroupSummary>>>().data
        }
    }
}
