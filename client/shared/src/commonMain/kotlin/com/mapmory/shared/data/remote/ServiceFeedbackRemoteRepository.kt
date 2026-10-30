package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ServiceFeedbackRequestDto
import com.mapmory.shared.domain.model.ServiceFeedbackCategory
import com.mapmory.shared.domain.model.ServiceFeedbackPlatform
import com.mapmory.shared.domain.repository.ServiceFeedbackRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class ServiceFeedbackRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
    private val accessTokenProvider: AccessTokenProvider,
) : ServiceFeedbackRepository {
    private val feedbackUrl = "${apiBaseUrl.trimEnd('/')}/service-feedback"

    override suspend fun submit(
        category: ServiceFeedbackCategory,
        content: String,
        platform: ServiceFeedbackPlatform,
    ): Result<Unit> = apiCall {
        client.post(feedbackUrl) {
            authorizeWith(accessTokenProvider)
            contentType(ContentType.Application.Json)
            setBody(ServiceFeedbackRequestDto(category.name, content.trim(), platform.name))
        }.requireSuccess()
    }
}
