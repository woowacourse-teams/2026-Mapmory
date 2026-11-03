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
import io.ktor.client.request.get
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import com.mapmory.shared.data.auth.*
import kotlinx.serialization.Serializable
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class AuthRemoteRepository(
    private val client: HttpClient,
    apiBaseUrl: String,
) : AuthGateway {
    private val authUrl = "${apiBaseUrl.trimEnd('/')}/auth"

    override suspend fun loginAsGuest(): Result<AuthTokens> = apiCall {
        client.post("$authUrl/login/guest") { timeout { requestTimeoutMillis = 20_000 } }
            .requireSuccess()
            .body<ApiResponseDto<GuestLoginResponseDto>>()
            .data
            .toAuthTokens()
    }

    override suspend fun socialLogin(provider: MemberProvider, idToken: String, accessToken: String?): Result<SocialSessionTokens> = apiCall {
        require(provider != MemberProvider.GUEST)
        val response = client.post("$authUrl/login/${provider.name.lowercase()}") {
            timeout { requestTimeoutMillis = 20_000 }
            contentType(ContentType.Application.Json)
            accessToken?.let { bearerAuth(it) }
            if (provider == MemberProvider.APPLE) setBody(AppleTokenRequest(idToken))
            else setBody(IdTokenRequest(idToken))
        }.requireSuccess().body<ApiResponseDto<SocialTokenResponse>>().data
        SocialSessionTokens(AuthTokens(response.accessToken, response.refreshToken), response.guestAbandoned)
    }

    override suspend fun member(accessToken: String): Result<AccountMember> = apiCall {
        client.get("${authUrl.removeSuffix("/auth")}/members/me") {
            timeout { requestTimeoutMillis = 20_000 }
            bearerAuth(accessToken)
        }.requireSuccess().body<ApiResponseDto<AccountMember>>().data
    }

    override suspend fun refresh(refreshToken: String): Result<AuthTokens> = apiCall {
        client.post("$authUrl/token/refresh") {
            timeout { requestTimeoutMillis = 20_000 }
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequestDto(refreshToken))
        }.requireSuccess()
            .body<ApiResponseDto<TokenResponseDto>>()
            .data
            .toAuthTokens()
    }

    override suspend fun logout(refreshToken: String): Result<Unit> = apiCall {
        client.post("$authUrl/logout") {
            timeout { requestTimeoutMillis = 20_000 }
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequestDto(refreshToken))
        }.requireSuccess()
        Unit
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

@Serializable private class IdTokenRequest(val idToken: String)
@Serializable private class AppleTokenRequest(val identityToken: String)
@Serializable private class SocialTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val guestAbandoned: Boolean = false,
)
