package com.mapmory.shared.data.remote

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth

/** 로그인 구현과 API 구현을 분리하기 위한 토큰 공급 경계다. */
fun interface AccessTokenProvider {
    fun getAccessToken(): String?
}

internal fun HttpRequestBuilder.authorizeWith(provider: AccessTokenProvider) {
    val token = provider.getAccessToken()?.takeIf(String::isNotBlank)
        ?: throw MissingAccessTokenException()
    bearerAuth(token)
}

class MissingAccessTokenException : IllegalStateException("로그인이 필요합니다.")
