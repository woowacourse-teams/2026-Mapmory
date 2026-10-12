package com.mapmory.backend.auth.infrastructure.google.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 ID token 검증 설정.
 *
 * clientIds   : ID token의 aud로 허용할 OAuth 클라이언트 ID 목록
 * jwkSetUri   : 구글 서명 공개키(JWKS) 주소
 */
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
        List<String> clientIds,
        String jwkSetUri
) {

    public GoogleProperties {
        clientIds = clientIds == null
                ? List.of()
                : clientIds.stream().map(String::strip).filter(id -> !id.isEmpty()).toList();
    }
}
