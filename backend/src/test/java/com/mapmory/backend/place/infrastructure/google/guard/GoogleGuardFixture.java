package com.mapmory.backend.place.infrastructure.google.guard;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 다른 폴더(places, geocoding)의 테스트가 실제 {@link GoogleCallGuard}로 클라이언트를 만들 때 쓴다.
 * 서킷 브레이커 조립은 이 폴더에 숨겨 두고(package-private) 테스트에만 열어 준다.
 */
public final class GoogleGuardFixture {

    /** application.yaml의 google.places.circuit-breaker와 같은 값이다. */
    public static final GoogleCircuitBreakerProperties BREAKER =
            new GoogleCircuitBreakerProperties(Duration.ofSeconds(60), 5, 50, Duration.ofSeconds(60), 3);

    /** 실패를 우리 에러로 바꾸며 남기는 로그(타임아웃 WARN, 거절 ERROR)의 로거 이름. */
    public static final String FAILURE_LOGGER = GoogleFailureTranslator.class.getName();

    /** 서킷 브레이커 상태 변화 로그의 로거 이름. */
    public static final String CIRCUIT_LOGGER = GoogleCircuitBreakerConfig.class.getName();

    private GoogleGuardFixture() {
    }

    public static Guarded guarded(GoogleRequestLimiter limiter, Clock clock) {
        GoogleCircuitBreakers breakers = breakers(clock);
        return new Guarded(new GoogleCallGuard(breakers, limiter), breakers::forApi);
    }

    public static GoogleCallGuard guard(GoogleRequestLimiter limiter, Clock clock) {
        return guarded(limiter, clock).guard();
    }

    /** 스프링 컨텍스트로 띄우는 테스트에 Guard와 호스트별 서킷 브레이커를 더한다. 한도(limiter)는 테스트가 넣는다. */
    public static ApplicationContextRunner withGuard(ApplicationContextRunner runner, Clock clock) {
        return runner.withBean(GoogleCircuitBreakers.class, () -> breakers(clock))
                .withUserConfiguration(GoogleCallGuard.class);
    }

    private static GoogleCircuitBreakers breakers(Clock clock) {
        return GoogleCircuitBreakers.of(host -> GoogleCircuitBreakerConfig.circuitBreaker(BREAKER, clock, host));
    }

    public record Guarded(GoogleCallGuard guard, Function<GoogleApi, CircuitBreaker> breakers) {

        /** 그 API의 호스트가 쓰는 서킷 브레이커. */
        public CircuitBreaker breakerOf(GoogleApi api) {
            return breakers.apply(api);
        }
    }
}
