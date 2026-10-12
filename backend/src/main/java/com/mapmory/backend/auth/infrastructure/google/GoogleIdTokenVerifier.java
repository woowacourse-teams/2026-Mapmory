package com.mapmory.backend.auth.infrastructure.google;

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
 * 앱이 전달한 구글 ID token을 검증한다.
 *
 * 카카오와 달리 사용자 정보 API를 호출하지 않는다. ID token은 구글이 서명한 JWT이므로
 * 공개키(JWKS)로 서명과 iss/aud/exp만 확인하면 된다. (ADR 0019)
 *
 * 오류를 구분한다.
 *   - 서명·형식·클레임이 잘못된 토큰 (사용자 책임) → INVALID_GOOGLE_TOKEN (401, 재로그인)
 *   - JWKS를 받아오지 못함 (구글 장애)          → GOOGLE_UNAVAILABLE (503, 재시도)
 */
@Component
class GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifier.class);

    private final JwtDecoder googleIdTokenDecoder;

    GoogleIdTokenVerifier(JwtDecoder googleIdTokenDecoder) {
        this.googleIdTokenDecoder = googleIdTokenDecoder;
    }

    GoogleUser verify(String idToken) {
        Jwt jwt = decode(idToken);
        return new GoogleUser(jwt.getSubject(), jwt.getClaimAsString("name"));
    }

    private Jwt decode(String idToken) {
        try {
            return googleIdTokenDecoder.decode(idToken);
        } catch (BadJwtException exception) {
            // 클라이언트 ID 설정 불일치(aud) 등을 운영 로그에서 구분할 수 있도록 거부 사유를 남긴다.
            log.info("구글 ID token 검증 실패: {}", exception.getMessage());
            throw new BusinessException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
        } catch (JwtException exception) {
            throw new BusinessException(
                    AuthErrorCode.GOOGLE_UNAVAILABLE,
                    AuthErrorCode.GOOGLE_UNAVAILABLE.detail(),
                    exception
            );
        }
    }
}
