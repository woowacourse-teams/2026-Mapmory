package com.mapmory.backend.auth.infrastructure.google.config;

import java.util.List;
import java.util.Set;
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
 * 구글이 안내하는 ID token 검증 규칙 중 서명 외의 항목.
 *
 * - exp/nbf : 만료되지 않았을 것 (exp가 없는 토큰도 거부한다)
 * - iss     : accounts.google.com 또는 https://accounts.google.com
 * - aud     : 우리 앱의 OAuth 클라이언트 ID 중 하나 (다른 앱용으로 발급된 토큰 재사용 차단)
 * - sub     : 비어 있지 않을 것 (provider_id로 저장한다)
 * - azp     : aud가 여러 개면 azp도 우리 클라이언트 ID여야 한다 (구글 권장)
 */
public final class GoogleIdTokenValidators {

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final String AUTHORIZED_PARTY = "azp";

    private GoogleIdTokenValidators() {
    }

    public static OAuth2TokenValidator<Jwt> create(List<String> clientIds) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setAllowEmptyExpiryClaim(false);
        return new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        issuer -> issuer != null && ISSUERS.contains(issuer.toString())),
                new JwtClaimValidator<String>(JwtClaimNames.SUB,
                        subject -> subject != null && !subject.isBlank()),
                audienceValidator(clientIds)
        );
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> clientIds) {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience == null || audience.stream().noneMatch(clientIds::contains)) {
                return invalid("The aud claim is not one of the configured Google client IDs");
            }
            String authorizedParty = jwt.getClaimAsString(AUTHORIZED_PARTY);
            if (audience.size() > 1 && (authorizedParty == null || !clientIds.contains(authorizedParty))) {
                return invalid("The azp claim is not one of the configured Google client IDs");
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2TokenValidatorResult invalid(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
