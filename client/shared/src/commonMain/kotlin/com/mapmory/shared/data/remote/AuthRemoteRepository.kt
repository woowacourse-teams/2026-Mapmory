package com.mapmory.shared.data.remote

import com.mapmory.shared.data.auth.AuthGateway
import com.mapmory.shared.data.auth.AuthTokens
import com.mapmory.shared.data.remote.model.ApiResponseDto
import com.mapmory.shared.data.remote.model.GuestLoginResponseDto
import com.mapmory.shared.data.remote.model.RefreshTokenRequestDto
import com.mapmory.shared.data.remote.model.TokenResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class AuthRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
) : AuthGateway {
    private val authUrl = "${apiBaseUrl.trimEnd('/')}/auth"

    override suspend fun loginAsGuest(): Result<AuthTokens> = apiCall {
        client.post("$authUrl/login/guest")
            .requireSuccess()
            .body<ApiResponseDto<GuestLoginResponseDto>>()
            .data
            .toAuthTokens()
    }

    override suspend fun refresh(refreshToken: String): Result<AuthTokens> = apiCall {
        client.post("$authUrl/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequestDto(refreshToken))
        }.requireSuccess()
            .body<ApiResponseDto<TokenResponseDto>>()
            .data
            .toAuthTokens()
    }
}

private fun GuestLoginResponseDto.toAuthTokens(): AuthTokens = AuthTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
)

private fun TokenResponseDto.toAuthTokens(): AuthTokens = AuthTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
)
