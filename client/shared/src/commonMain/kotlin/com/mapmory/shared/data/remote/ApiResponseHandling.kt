package com.mapmory.shared.data.remote

import com.mapmory.shared.data.remote.model.ProblemDetailDto
import com.mapmory.shared.data.remote.model.ProblemFieldErrorDto
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse

internal suspend fun HttpResponse.requireSuccess(): HttpResponse {
    if (status.value in 200..299) return this

    val problem = apiCall { body<ProblemDetailDto>() }.getOrNull()
    throw MapmoryApiException(
        statusCode = status.value,
        code = problem?.code ?: "HTTP_${status.value}",
        title = problem?.title,
        detail = problem?.detail,
        instance = problem?.instance,
        errors = problem?.errors.orEmpty(),
    )
}

class MapmoryApiException(
    val statusCode: Int,
    val code: String,
    val title: String?,
    val detail: String?,
    val instance: String?,
    val errors: List<ProblemFieldErrorDto>,
) : IllegalStateException(detail ?: title ?: "API 요청에 실패했습니다.")
