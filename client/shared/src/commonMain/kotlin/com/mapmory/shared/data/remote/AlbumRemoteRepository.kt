package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.GuestSessionManager
import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.domain.group.AlbumDetail
import com.mapmory.shared.domain.group.AlbumPage
import com.mapmory.shared.domain.repository.AlbumRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable private data class CreateAlbumRequest(val travelRecordId: Long, val name: String?, val regionId: Long?)
@Serializable private data class AddRecordRequest(val travelRecordId: Long)
@Serializable private data class UpdateAlbumRequest(val name: String?, val regionId: Long?)

internal class AlbumRemoteRepository(private val client: HttpClient, private val apiBaseUrl: String, private val session: GuestSessionManager) : AlbumRepository {
    override suspend fun list(groupId: Long, page: Int) = authenticated {
        request<AlbumPage>(HttpMethod.Get, groupId, null) { parameter("page", page); parameter("size", 20) }
    }
    override suspend fun detail(groupId: Long, albumId: Long) = authenticated { request<AlbumDetail>(HttpMethod.Get, groupId, albumId) }
    override suspend fun create(groupId: Long, recordId: Long, name: String?, regionId: Long?) = authenticated {
        request<AlbumDetail>(HttpMethod.Post, groupId, null) { contentType(ContentType.Application.Json); setBody(CreateAlbumRequest(recordId, name, regionId)) }
    }
    override suspend fun addRecord(groupId: Long, albumId: Long, recordId: Long) = authenticated {
        request<AlbumDetail>(HttpMethod.Post, groupId, albumId, "entries") { contentType(ContentType.Application.Json); setBody(AddRecordRequest(recordId)) }
    }
    override suspend fun update(groupId: Long, albumId: Long, name: String?, regionId: Long?) = authenticated {
        request<AlbumDetail>(HttpMethod.Patch, groupId, albumId) { contentType(ContentType.Application.Json); setBody(UpdateAlbumRequest(name, regionId)) }
    }
    private suspend fun <T> authenticated(block: suspend () -> T): Result<T> = session.withAuthenticatedSession { apiCall { block() } }
    private suspend inline fun <reified T> request(method: HttpMethod, groupId: Long, albumId: Long?, suffix: String? = null, crossinline configure: HttpRequestBuilder.() -> Unit = {}): T = client.request(apiBaseUrl.trimEnd('/')) {
        this.method = method
        url { appendPathSegments(*(listOf("groups", groupId.toString(), "albums") + listOfNotNull(albumId?.toString(), suffix)).toTypedArray()) }
        bearerAuth(checkNotNull(session.getAccessToken()))
        configure()
    }.requireSuccess().body<ApiResponseDto<T>>().data
}
