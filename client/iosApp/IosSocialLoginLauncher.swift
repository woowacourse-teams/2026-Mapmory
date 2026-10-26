import UIKit
import AuthenticationServices
import CryptoKit
import GoogleSignIn
import KakaoSDKCommon
import KakaoSDKAuth
import KakaoSDKUser
import Shared

final class IosSocialLoginLauncher: NSObject, SocialLoginLauncher, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    let providers: [SocialProvider] = [.google, .kakao, .apple]
    private var completion: ((SocialLoginResult) -> Void)?
    private var appleController: ASAuthorizationController?
    private var nonce: String?
    private var anchor: UIWindow?

    func launch(provider: SocialProvider, onResult: @escaping (SocialLoginResult) -> Void) {
        guard completion == nil else { onResult(SocialLoginFailure(message: "로그인이 진행 중이에요.")); return }
        guard let window = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows }).first(where: { $0.isKeyWindow }),
              var presenter = window.rootViewController else {
            onResult(SocialLoginFailure(message: "로그인 화면을 열 수 없어요.")); return
        }
        while let presented = presenter.presentedViewController { presenter = presented }
        anchor = window
        completion = onResult
        if provider == .google {
            guard let clientID = configured("MAPMORY_GOOGLE_IOS_CLIENT_ID"),
                  let serverID = configured("MAPMORY_GOOGLE_WEB_CLIENT_ID") else {
                finish(SocialLoginFailure(message: "Google 로그인 설정을 준비 중이에요.")); return
            }
            GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID, serverClientID: serverID)
            GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { [weak self] result, error in
                guard let self else { return }
                if (error as NSError?)?.code == GIDSignInError.canceled.rawValue {
                    finish(SocialLoginCancelled.shared)
                } else if let token = result?.user.idToken?.tokenString {
                    finish(SocialLoginSuccess(credential: SocialCredential(provider: .google, idToken: token,
                        accessToken: nil, authorizationCode: result?.serverAuthCode, displayName: result?.user.profile?.name, rawNonce: nil)))
                } else { finish(SocialLoginFailure(message: "Google 인증에 실패했어요.")) }
            }
        } else if provider == .kakao {
            guard let key = configured("MAPMORY_KAKAO_NATIVE_APP_KEY") else {
                finish(SocialLoginFailure(message: "카카오 로그인 설정을 준비 중이에요.")); return
            }
            KakaoSDK.initSDK(appKey: key)
            let callback: (OAuthToken?, Error?) -> Void = { [weak self] token, error in
                if let token, let idToken = token.idToken, !idToken.isEmpty {
                    self?.finish(SocialLoginSuccess(credential: SocialCredential(provider: .kakao, idToken: idToken,
                        accessToken: nil, authorizationCode: nil, displayName: nil, rawNonce: nil)))
                } else if token != nil && error == nil {
                    self?.finish(SocialLoginFailure(message: "카카오 ID 토큰을 받지 못했어요. 앱의 OpenID Connect 설정을 확인해 주세요."))
                } else if let error = error as? SdkError, error.isClientFailed {
                    if case .ClientFailed(let reason, _) = error, reason == .Cancelled {
                        self?.finish(SocialLoginCancelled.shared)
                    } else { self?.finish(SocialLoginFailure(message: "카카오 인증에 실패했어요.")) }
                } else { self?.finish(SocialLoginFailure(message: "카카오 인증에 실패했어요.")) }
            }
            if UserApi.isKakaoTalkLoginAvailable() {
                UserApi.shared.loginWithKakaoTalk(completion: callback)
            } else { UserApi.shared.loginWithKakaoAccount(completion: callback) }
        } else {
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            // Server must verify this nonce when its exchange endpoint is connected.
            let rawNonce = UUID().uuidString + UUID().uuidString
            nonce = rawNonce
            request.nonce = SHA256.hash(data: Data(rawNonce.utf8)).map { String(format: "%02x", $0) }.joined()
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self; controller.presentationContextProvider = self
            appleController = controller
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let data = credential.identityToken, let token = String(data: data, encoding: .utf8) else {
            finish(SocialLoginFailure(message: "Apple 인증을 확인하지 못했어요.")); return
        }
        let name = credential.fullName.map { PersonNameComponentsFormatter().string(from: $0) }
        // The name can be absent on subsequent authorizations. No empty-name overwrite here.
        finish(SocialLoginSuccess(credential: SocialCredential(provider: .apple, idToken: token, accessToken: nil,
            authorizationCode: credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }, displayName: name, rawNonce: nonce)))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        finish((error as? ASAuthorizationError)?.code == .canceled
            ? SocialLoginCancelled.shared : SocialLoginFailure(message: "Apple 인증에 실패했어요."))
    }
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor { anchor! }
    private func finish(_ result: SocialLoginResult) {
        let callback = completion; completion = nil; appleController = nil; nonce = nil; anchor = nil
        callback?(result)
    }
    private func configured(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
              !value.isEmpty, !value.contains("$(") else { return nil }
        return value
    }
    static func handle(_ url: URL) -> Bool {
        if AuthApi.isKakaoTalkLoginUrl(url) { return AuthController.handleOpenUrl(url: url) }
        return GIDSignIn.sharedInstance.handle(url)
    }
}
