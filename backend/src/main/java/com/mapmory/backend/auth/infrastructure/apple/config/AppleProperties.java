package com.mapmory.backend.auth.infrastructure.apple.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플 identity token 검증과 토큰 철회 설정.
 *
 * clientIds   : identity token의 aud로 허용할 값 목록. iOS 앱은 번들 ID, 웹(Services ID)은 Services ID다.
 * jwkSetUri   : 애플 서명 공개키(JWKS) 주소
 *
 * 아래는 탈퇴할 때 애플 토큰을 철회하는 데만 쓴다. 로그인에는 필요 없다. (ADR 0025)
 * teamId      : 애플 개발자 팀 ID. client_secret의 iss
 * keyId       : Sign in with Apple 키 ID. client_secret 헤더의 kid
 * privateKey  : 그 키의 .p8 내용(PKCS#8 PEM). 비밀값이다
 * tokenUri    : authorization code를 토큰으로 바꾸는 주소
 * revokeUri   : 토큰을 철회하는 주소
 */
@ConfigurationProperties(prefix = "apple")
public record AppleProperties(
        List<String> clientIds,
        String jwkSetUri,
        String teamId,
        String keyId,
        String privateKey,
        String tokenUri,
        String revokeUri
) {

    public AppleProperties {
        clientIds = clientIds == null
                ? List.of()
                : clientIds.stream().map(String::strip).filter(id -> !id.isEmpty()).toList();
    }

    public boolean canRevoke() {
        return hasText(teamId) && hasText(keyId) && hasText(privateKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
