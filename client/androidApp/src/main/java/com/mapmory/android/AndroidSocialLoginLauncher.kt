package com.mapmory.android

import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.mapmory.shared.presentation.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AndroidSocialLoginLauncher(private val activity: ComponentActivity) : SocialLoginLauncher {
    override val providers = listOf(SocialProvider.GOOGLE, SocialProvider.KAKAO)
    override fun launch(provider: SocialProvider, onResult: (SocialLoginResult) -> Unit) {
        when (provider) {
            SocialProvider.GOOGLE -> activity.lifecycleScope.launch {
                val clientId = activity.getString(R.string.mapmory_google_web_client_id)
                if (clientId.isBlank()) {
                    onResult(SocialLoginFailure("Google 로그인 설정을 준비 중이에요.")); return@launch
                }
                try {
                    val request = GetCredentialRequest(listOf(GetSignInWithGoogleOption.Builder(clientId).build()))
                    val response = CredentialManager.create(activity).getCredential(activity, request)
                    val proof = GoogleIdTokenCredential.createFrom(response.credential.data)
                    onResult(SocialLoginSuccess(SocialCredential(provider, idToken = proof.idToken, displayName = proof.displayName)))
                } catch (_: GetCredentialCancellationException) {
                    onResult(SocialLoginCancelled)
                } catch (error: CancellationException) { throw error
                } catch (_: Exception) { onResult(SocialLoginFailure("Google 인증에 실패했어요. 다시 시도해 주세요.")) }
            }
            SocialProvider.KAKAO -> {
                val key = activity.getString(R.string.mapmory_kakao_native_app_key)
                if (key.isBlank()) { onResult(SocialLoginFailure("카카오 로그인 설정을 준비 중이에요.")); return }
                KakaoSdk.init(activity.applicationContext, key)
                val callback: (com.kakao.sdk.auth.model.OAuthToken?, Throwable?) -> Unit = { token, error ->
                    when {
                        error is ClientError && error.reason == ClientErrorCause.Cancelled -> onResult(SocialLoginCancelled)
                        token != null -> onResult(SocialLoginSuccess(SocialCredential(provider, accessToken = token.accessToken)))
                        else -> onResult(SocialLoginFailure("카카오 인증에 실패했어요. 다시 시도해 주세요."))
                    }
                }
                if (UserApiClient.instance.isKakaoTalkLoginAvailable(activity)) {
                    UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
                        if (error != null && !(error is ClientError && error.reason == ClientErrorCause.Cancelled)) {
                            UserApiClient.instance.loginWithKakaoAccount(activity, callback = callback)
                        } else callback(token, error)
                    }
                } else UserApiClient.instance.loginWithKakaoAccount(activity, callback = callback)
            }
            SocialProvider.APPLE -> onResult(SocialLoginFailure("Apple 로그인은 iOS에서 지원돼요."))
        }
    }
}
