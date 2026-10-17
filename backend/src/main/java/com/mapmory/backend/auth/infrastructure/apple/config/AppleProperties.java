package com.mapmory.backend.auth.infrastructure.apple.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플 identity token 검증 설정.
 *
 * clientIds   : identity token의 aud로 허용할 값 목록. iOS 앱은 번들 ID, 웹(Services ID)은 Services ID다.
 * jwkSetUri   : 애플 서명 공개키(JWKS) 주소
 */
@ConfigurationProperties(prefix = "apple")
public record AppleProperties(
        List<String> clientIds,
        String jwkSetUri
) {

    public AppleProperties {
        clientIds = clientIds == null
                ? List.of()
                : clientIds.stream().map(String::strip).filter(id -> !id.isEmpty()).toList();
    }
}
