package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.StateTransition;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Google 호출 앞에 두는 서킷 브레이커를 만든다. 열렸을 때의 응답과 월 한도 환불은 {@link GooglePlacesClient}가 정한다.
 *
 * <p>Google이 멈췄다는 신호인 타임아웃·연결 오류({@link ResourceAccessException})와 5xx만 실패로 센다.
 * 4xx는 Google이 답한 것이라 성공으로 세고, 우리 한도 거절({@link BusinessException})은 Google을 부르지 않았으니 세지 않는다.
 * 호출이 분당 60회를 넘지 않아 최근 몇 번이 아니라 최근 몇 초로 센다.
 *
 * <p>닫혀 있다 열릴 때만 ERROR 로그를 남겨 운영 ERROR 로그 알람(Discord)이 장애 하나에 한 번 울린다.
 * 장애 동안 시험 뒤 다시 열리는 것과 닫히는 것은 WARN이다.
 */
@Configuration
@EnableConfigurationProperties(GoogleCircuitBreakerProperties.class)
class GoogleCircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleCircuitBreakerConfig.class);

    @Bean
    CircuitBreaker googlePlacesCircuitBreaker(GoogleCircuitBreakerProperties properties) {
        return circuitBreaker(properties, Clock.systemUTC());
    }

    static CircuitBreaker circuitBreaker(GoogleCircuitBreakerProperties properties, Clock clock) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindow((int) properties.window().toSeconds(), properties.minimumCalls(),
                        SlidingWindowType.TIME_BASED)
                .failureRateThreshold(properties.failureRatePercent())
                .waitDurationInOpenState(properties.openDuration())
                .permittedNumberOfCallsInHalfOpenState(properties.halfOpenCalls())
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .ignoreExceptions(BusinessException.class)
                .clock(clock)
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("googlePlaces", config);
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            StateTransition transition = event.getStateTransition();
            log.atLevel(transition == StateTransition.CLOSED_TO_OPEN ? Level.ERROR : Level.WARN)
                    .addKeyValue("event", "GOOGLE_PLACES_CIRCUIT")
                    .addKeyValue("transition", transition.name())
                    .log("Google 서킷 브레이커 상태가 바뀌었다: {}", transition);
        });
        return circuitBreaker;
    }
}
