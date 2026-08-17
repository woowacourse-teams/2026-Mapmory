package com.mapmory.backend.auth.kakao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KakaoProperties.class)
public class KakaoClientConfig {

    /**
     * 카카오 호출 전용 RestClient.
     *
     * 앱 전역 자동 구성 빌더에 의존하지 않고 직접 생성한다. (외부 호출에 앱의 인터셉터·컨버터를
     * 얹지 않고, 자동 구성 여부와 무관하게 동작하게 하기 위함)
     */
    @Bean
    public RestClient kakaoRestClient() {
        return RestClient.create();
    }
}
