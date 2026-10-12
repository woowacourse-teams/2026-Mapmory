package com.mapmory.backend.auth.infrastructure.google.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleClientConfig.class);

    /**
     * 구글 JWKS로 서명을 검증하는 디코더. 공개키는 디코더가 내려받아 캐시하고,
     * 처음 보는 kid가 오면 다시 내려받는다.
     *
     * JWKS 호출에도 공통 HTTP 타임아웃이 적용되도록 Spring Boot가 구성한 Builder를 사용한다.
     */
    @Bean
    public JwtDecoder googleIdTokenDecoder(GoogleProperties googleProperties, RestTemplateBuilder builder) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(googleProperties.jwkSetUri())
                .restOperations(builder.build())
                .build();
        if (googleProperties.clientIds().isEmpty()) {
            // 앱은 정상적으로 뜨지만 모든 구글 로그인이 401로 거부되므로, 설정 누락을 로그로 드러낸다.
            log.warn("google.client-ids(GOOGLE_CLIENT_IDS)가 비어 있어 모든 구글 로그인이 거부됩니다.");
        }
        decoder.setJwtValidator(GoogleIdTokenValidators.create(googleProperties.clientIds()));
        return decoder;
    }
}
