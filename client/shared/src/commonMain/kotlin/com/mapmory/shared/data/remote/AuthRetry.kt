package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.GuestSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url

/**
 * Mapmory 보호 API의 401을 공통 HTTP 경계에서 처리한다.
 *
 * Repository가 요청을 통째로 다시 실행하면 이미 완료된 S3 업로드까지 반복될 수 있다.
 * 따라서 실패한 HTTP 요청 하나만 새 Access Token으로 최대 한 번 재전송한다.
 */
internal fun HttpClient.installMapmoryAuthRetry(
    session: GuestSessionManager,
    apiBaseUrl: String,
) {
    val apiUrl = Url(apiBaseUrl)

    plugin(HttpSend).intercept { request ->
        val failedAccessToken = request.headers[HttpHeaders.Authorization].bearerTokenOrNull()
        val response = execute(request)

        if (
            response.response.status != HttpStatusCode.Unauthorized ||
            failedAccessToken == null ||
            !request.url.isSameApi(apiUrl)
        ) {
            return@intercept response
        }

        session.refreshAfterUnauthorized(failedAccessToken).getOrThrow()
        val refreshedAccessToken = session.getAccessToken()
            ?: throw MissingAccessTokenException()

        request.headers.remove(HttpHeaders.Authorization)
        request.bearerAuth(refreshedAccessToken)
        execute(request)
    }
}

private fun String?.bearerTokenOrNull(): String? {
    val value = this?.trim().orEmpty()
    if (!value.startsWith(BearerPrefix, ignoreCase = true)) return null
    return value.substring(BearerPrefix.length).trim().takeIf(String::isNotEmpty)
}

private fun io.ktor.http.URLBuilder.isSameApi(apiUrl: Url): Boolean = build().let { requestUrl ->
    val apiPath = apiUrl.encodedPath.trimEnd('/')
    requestUrl.protocol == apiUrl.protocol &&
        requestUrl.host == apiUrl.host &&
        requestUrl.port == apiUrl.port &&
        (requestUrl.encodedPath == apiPath || requestUrl.encodedPath.startsWith("$apiPath/"))
}

private const val BearerPrefix = "Bearer "
