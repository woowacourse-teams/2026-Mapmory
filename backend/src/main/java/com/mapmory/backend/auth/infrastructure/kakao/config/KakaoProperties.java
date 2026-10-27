package com.mapmory.backend.auth.infrastructure.kakao.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 ID token 검증 설정.
 *
 * clientIds   : ID token의 aud로 허용할 앱 키 목록. 네이티브 SDK 로그인은 네이티브 앱 키다.
 * jwkSetUri   : 카카오 서명 공개키(JWKS) 주소
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        List<String> clientIds,
        String jwkSetUri
) {

    public KakaoProperties {
        clientIds = clientIds == null
                ? List.of()
                : clientIds.stream().map(String::strip).filter(id -> !id.isEmpty()).toList();
    }
}
