package com.mapmory.backend.auth.infrastructure.kakao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoClientConfig {

    private static final Logger log = LoggerFactory.getLogger(KakaoClientConfig.class);

    /**
     * 카카오 JWKS로 서명을 검증하는 디코더. 공개키는 디코더가 내려받아 캐시하고,
     * 처음 보는 kid가 오면 다시 내려받는다. 카카오는 공개키를 너무 자주 받으면 요청을 막을 수 있어 캐시가 필요하다.
     *
     * JWKS 호출에도 공통 HTTP 타임아웃이 적용되도록 Spring Boot가 구성한 Builder를 사용한다.
     */
    @Bean
    public JwtDecoder kakaoIdTokenDecoder(KakaoProperties kakaoProperties, RestTemplateBuilder builder) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(kakaoProperties.jwkSetUri())
                .restOperations(builder.build())
                .build();
        if (kakaoProperties.clientIds().isEmpty()) {
            // 앱은 정상적으로 뜨지만 모든 카카오 로그인이 401로 거부되므로, 설정 누락을 로그로 드러낸다.
            log.warn("kakao.client-ids(KAKAO_CLIENT_IDS)가 비어 있어 모든 카카오 로그인이 거부됩니다.");
        }
        decoder.setJwtValidator(KakaoIdTokenValidators.create(kakaoProperties.clientIds()));
        return decoder;
    }
}
