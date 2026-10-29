package com.mapmory.backend.auth.infrastructure.apple;

import com.mapmory.backend.auth.application.model.AppleReauthentication;
import com.mapmory.backend.auth.application.port.AppleRevocationPort;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 애플 회원 탈퇴의 재인증 확인과 토큰 철회. (ADR 0025)
 *
 * identity token 검증은 로그인과 같은 규칙을 쓴다. 다만 로그인과 달리 보호된 경로에서 401을 주면 앱이
 * 우리 로그인이 끊긴 것으로 볼 수 있으므로, 잘못된 identity token은 재인증 실패(400)로 바꾼다.
 */
@Component
public class AppleRevocationAdapter implements AppleRevocationPort {

    private final AppleIdentityTokenVerifier appleIdentityTokenVerifier;
    private final AppleTokenClient appleTokenClient;

    AppleRevocationAdapter(AppleIdentityTokenVerifier appleIdentityTokenVerifier, AppleTokenClient appleTokenClient) {
        this.appleIdentityTokenVerifier = appleIdentityTokenVerifier;
        this.appleTokenClient = appleTokenClient;
    }

    @Override
    public AppleReauthentication verify(String identityToken) {
        try {
            AppleUser appleUser = appleIdentityTokenVerifier.verify(identityToken);
            return new AppleReauthentication(appleUser.subject(), appleUser.clientId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == AuthErrorCode.INVALID_APPLE_TOKEN) {
                throw new BusinessException(AuthErrorCode.APPLE_REAUTHENTICATION_FAILED);
            }
            throw exception;
        }
    }

    @Override
    public void revoke(AppleReauthentication reauthentication, String authorizationCode) {
        appleTokenClient.revoke(reauthentication, authorizationCode);
    }
}
