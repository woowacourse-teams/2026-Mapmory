package com.mapmory.backend.auth.infrastructure.apple;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * 앱이 전달한 애플 identity token을 검증한다.
 *
 * 구글과 같이 애플이 서명한 JWT이므로 공개키(JWKS)로 서명과 iss/aud/exp/sub만 확인한다.
 * 애플은 identity token에 이름을 넣지 않으므로 여기서는 sub만 꺼낸다. (ADR 0020)
 *
 * 오류를 구분한다.
 *   - 서명·형식·클레임이 잘못된 토큰 (사용자 책임) → INVALID_APPLE_TOKEN (401, 재로그인)
 *   - JWKS를 받아오지 못함 (애플 장애)          → APPLE_UNAVAILABLE (503, 재시도)
 */
@Component
class AppleIdentityTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(AppleIdentityTokenVerifier.class);

    private final JwtDecoder appleIdentityTokenDecoder;

    AppleIdentityTokenVerifier(JwtDecoder appleIdentityTokenDecoder) {
        this.appleIdentityTokenDecoder = appleIdentityTokenDecoder;
    }

    AppleUser verify(String identityToken) {
        return new AppleUser(decode(identityToken).getSubject());
    }

    private Jwt decode(String identityToken) {
        try {
            return appleIdentityTokenDecoder.decode(identityToken);
        } catch (BadJwtException exception) {
            // 번들 ID 설정 불일치(aud) 등을 운영 로그에서 구분할 수 있도록 거부 사유를 남긴다.
            log.info("애플 identity token 검증 실패: {}", exception.getMessage());
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_TOKEN);
        } catch (JwtException exception) {
            throw new BusinessException(
                    AuthErrorCode.APPLE_UNAVAILABLE,
                    AuthErrorCode.APPLE_UNAVAILABLE.detail(),
                    exception
            );
        }
    }
}
