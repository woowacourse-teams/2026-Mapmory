package com.mapmory.backend.place.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.domain.SearchQuery;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

/**
 * Google이 늦게 답할 때 application.yaml의 google.places.read-timeout(1초)에서 끊는지 확인한다.
 *
 * <p>공통 값(spring.http.clients.read-timeout 3초)이 아니라 Google 전용 값이 적용돼야 한다.
 */
class GooglePlacesTimeoutTest {

    private static final String CANDIDATES = """
            {"suggestions":[{"placePrediction":{"placeId":"ChIJ-gyeong","text":{"text":"경복궁"}}}]}
            """;

    private HttpServer google;
    private volatile long delayMillis;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            // 앱 기동(SpringApplication)과 같이 "1s" 같은 값을 Duration으로 바꾼다.
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(GoogleRequestLimiter.class, () -> mock(GoogleRequestLimiter.class))
            .withBean(CircuitBreaker.class, () -> GoogleCircuitBreakerConfig.circuitBreaker(
                    new GoogleCircuitBreakerProperties(Duration.ofSeconds(60), 5, 50, Duration.ofSeconds(60), 3),
                    Clock.systemUTC()))
            .withUserConfiguration(GooglePlacesClient.class);

    @BeforeEach
    void startGoogle() throws IOException {
        google = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        google.createContext("/", exchange -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] body = CANDIDATES.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        google.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        google.start();
    }

    @AfterEach
    void stopGoogle() {
        google.stop(0);
    }

    @Test
    void 응답이_1초를_넘기면_기다리지_않고_제공자_장애로_끝낸다() {
        delayMillis = 2_000;
        contextRunner.withPropertyValues(properties()).run(context -> {
            GooglePlacesClient client = context.getBean(GooglePlacesClient.class);

            assertTimeoutPreemptively(Duration.ofMillis(1_800), () ->
                    assertThatThrownBy(() -> client.search(SearchQuery.from("경복"), "session-1"))
                            .isInstanceOf(BusinessException.class)
                            .extracting(exception -> ((BusinessException) exception).getErrorCode())
                            .isEqualTo(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE));
        });
    }

    @Test
    void 측정한_p99보다_느려도_1초_안이면_정상으로_받는다() {
        delayMillis = 600;
        contextRunner.withPropertyValues(properties()).run(context -> {
            GooglePlacesClient client = context.getBean(GooglePlacesClient.class);

            assertThat(client.search(SearchQuery.from("경복"), "session-1")).hasSize(1);
        });
    }

    private String[] properties() {
        return new String[] {
                "google.places.base-url=http://127.0.0.1:" + google.getAddress().getPort(),
                "google.places.api-key=test-key"
        };
    }
}
