package com.mapmory.backend.auth.infrastructure.kakao;

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
 * 앱이 전달한 카카오 ID token을 검증한다.
 *
 * 구글·애플과 같이 카카오가 서명한 JWT이므로 공개키(JWKS)로 서명과 iss/aud/exp/sub만 확인한다. (ADR 0024)
 *
 * 오류를 구분한다.
 *   - 서명·형식·클레임이 잘못된 토큰 (사용자 책임) → INVALID_KAKAO_TOKEN (401, 재로그인)
 *   - JWKS를 받아오지 못함 (카카오 장애)          → KAKAO_UNAVAILABLE (503, 재시도)
 */
@Component
class KakaoIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(KakaoIdTokenVerifier.class);
    private static final String NICKNAME = "nickname";

    private final JwtDecoder kakaoIdTokenDecoder;

    KakaoIdTokenVerifier(JwtDecoder kakaoIdTokenDecoder) {
        this.kakaoIdTokenDecoder = kakaoIdTokenDecoder;
    }

    KakaoUser verify(String idToken) {
        Jwt jwt = decode(idToken);
        return new KakaoUser(jwt.getSubject(), jwt.getClaimAsString(NICKNAME));
    }

    private Jwt decode(String idToken) {
        try {
            return kakaoIdTokenDecoder.decode(idToken);
        } catch (BadJwtException exception) {
            // 앱 키 설정 불일치(aud) 등을 운영 로그에서 구분할 수 있도록 거부 사유를 남긴다.
            log.info("카카오 ID token 검증 실패: {}", exception.getMessage());
            throw new BusinessException(AuthErrorCode.INVALID_KAKAO_TOKEN);
        } catch (JwtException exception) {
            throw new BusinessException(
                    AuthErrorCode.KAKAO_UNAVAILABLE,
                    AuthErrorCode.KAKAO_UNAVAILABLE.detail(),
                    exception
            );
        }
    }
}
