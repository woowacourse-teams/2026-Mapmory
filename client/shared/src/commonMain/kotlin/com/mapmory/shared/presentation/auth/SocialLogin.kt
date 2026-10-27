package com.mapmory.shared.presentation.auth

import androidx.compose.runtime.staticCompositionLocalOf

enum class SocialProvider { GOOGLE, KAKAO, APPLE }

/** Provider proof is ephemeral: never a Mapmory access token and never logged/persisted here. */
class SocialCredential(
    val provider: SocialProvider,
    val idToken: String? = null,
    val accessToken: String? = null,
    val authorizationCode: String? = null,
    val displayName: String? = null,
    val rawNonce: String? = null,
) {
    init { require(listOf(idToken, accessToken, authorizationCode).any { !it.isNullOrBlank() }) }
    override fun toString() = "SocialCredential(provider=$provider, proof=***)"
}

sealed interface SocialLoginResult
class SocialLoginSuccess(val credential: SocialCredential) : SocialLoginResult
object SocialLoginCancelled : SocialLoginResult
class SocialLoginFailure(val message: String) : SocialLoginResult

/** Callback boundary makes Swift/Android SDKs replaceable without deciding the backend DTO. */
interface SocialLoginLauncher {
    val providers: List<SocialProvider>
    fun launch(provider: SocialProvider, onResult: (SocialLoginResult) -> Unit)
}

val LocalSocialLoginLauncher = staticCompositionLocalOf<SocialLoginLauncher?> { null }
