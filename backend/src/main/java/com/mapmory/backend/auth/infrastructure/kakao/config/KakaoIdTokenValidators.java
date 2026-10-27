package com.mapmory.backend.auth.infrastructure.kakao.config;

import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * 카카오가 안내하는 ID token 검증 규칙 중 서명 외의 항목.
 *
 * - exp/nbf : 만료되지 않았을 것 (exp가 없는 토큰도 거부한다)
 * - iss     : https://kauth.kakao.com
 * - aud     : 우리 앱 키(네이티브 앱 키 등) 중 하나 (다른 앱용으로 발급된 토큰 재사용 차단)
 * - sub     : 비어 있지 않을 것 (provider_id로 저장한다)
 */
public final class KakaoIdTokenValidators {

    private static final String ISSUER = "https://kauth.kakao.com";

    private KakaoIdTokenValidators() {
    }

    public static OAuth2TokenValidator<Jwt> create(List<String> clientIds) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setAllowEmptyExpiryClaim(false);
        return new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        issuer -> issuer != null && ISSUER.equals(issuer.toString())),
                new JwtClaimValidator<String>(JwtClaimNames.SUB,
                        subject -> subject != null && !subject.isBlank()),
                audienceValidator(clientIds)
        );
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> clientIds) {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience == null || audience.stream().noneMatch(clientIds::contains)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
                        "The aud claim is not one of the configured Kakao client IDs", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }
}
