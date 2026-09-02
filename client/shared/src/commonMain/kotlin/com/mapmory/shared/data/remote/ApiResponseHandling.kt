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
) : IllegalStateException(userFacingApiMessage(code, title, detail))

private fun userFacingApiMessage(
    code: String,
    title: String?,
    detail: String?,
): String = when (code) {
    "INVALID_OBJECT_KEY" ->
        "사진을 저장하지 못했습니다. 잠시 후 다시 저장해 주세요."

    "INVALID_ACCESS_TOKEN",
    "EXPIRED_ACCESS_TOKEN",
    "INVALID_REFRESH_TOKEN",
    "EXPIRED_REFRESH_TOKEN" -> "로그인 정보가 만료되었습니다. 다시 로그인해 주세요."

    "INVALID_KAKAO_TOKEN" -> "카카오 로그인 정보를 확인하지 못했습니다. 다시 로그인해 주세요."
    else -> detail ?: title ?: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
}
