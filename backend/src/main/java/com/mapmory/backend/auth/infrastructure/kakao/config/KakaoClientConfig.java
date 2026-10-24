package com.mapmory.backend.auth.infrastructure.kakao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoClientConfig {

    private static final Logger log = LoggerFactory.getLogger(KakaoClientConfig.class);

    /**
     * Spring Boot가 자동 구성한 Builder를 사용한다.
     * 이를 통해 HTTP 메시지 변환, 타임아웃 설정과 관측 기능이 적용된다.
     * 자동 빌더를 사용하지 않으면 외부 api 관측이 불가능하다고 한다.
     */
    @Bean
    public RestClient kakaoRestClient(RestClient.Builder builder, KakaoProperties kakaoProperties) {
        if (kakaoProperties.appId() == null) {
            // 앱은 정상적으로 뜨지만 모든 카카오 로그인이 401로 거부되므로, 설정 누락을 로그로 드러낸다.
            log.warn("kakao.app-id(KAKAO_APP_ID)가 비어 있어 모든 카카오 로그인이 거부됩니다.");
        }
        return builder.build();
    }
}
