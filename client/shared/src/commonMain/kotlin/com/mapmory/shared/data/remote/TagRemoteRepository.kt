package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.TagDto
import com.mapmory.shared.data.remote.model.TagRequestDto
import com.mapmory.shared.data.remote.model.toDomain
import com.mapmory.shared.domain.model.Tag
import com.mapmory.shared.domain.repository.TagRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class TagRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : TagRepository {
    private val tagsUrl = "${apiBaseUrl.trimEnd('/')}/tags"

    override suspend fun getTags(): Result<List<Tag>> = apiCall {
        client.get(tagsUrl) {
            authorizeWith(accessTokenProvider)
        }.requireSuccess()
            .body<ApiResponseDto<List<TagDto>>>()
            .data
            .map(TagDto::toDomain)
    }

    override suspend fun createTag(name: String): Result<Tag> = apiCall {
        client.post(tagsUrl) {
            authorizeWith(accessTokenProvider)
            contentType(ContentType.Application.Json)
            setBody(TagRequestDto(name))
        }.requireSuccess()
            .body<ApiResponseDto<TagDto>>()
            .data
            .toDomain()
    }
}
