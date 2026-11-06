package com.mapmory.backend.place.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.domain.SearchQuery;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 실제 서버로 application.yaml의 Google 전용 값(connect 400ms, read 1초, 풀 크기 = 분당 한도)이 걸리는지 확인한다.
 *
 * <p>공통 값(spring.http.clients 2초/3초)이 아니라 Google 전용 값이 적용돼야 한다.
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
            .withBean(GoogleCircuitBreakers.class, () -> GoogleCircuitBreakers.of(host -> GoogleCircuitBreakerConfig.circuitBreaker(
                    new GoogleCircuitBreakerProperties(Duration.ofSeconds(60), 5, 50, Duration.ofSeconds(60), 3),
                    Clock.systemUTC(), host)))
            .withUserConfiguration(GoogleRestClientConfig.class, GoogleCallGuard.class, GooglePlacesClient.class);

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
        google.setExecutor(Executors.newCachedThreadPool());
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
    void Apache_HttpClient로_부른다() {
        Object factory = GoogleRestClientConfig.requestFactory(
                new GoogleApiProperties("test-key", "https://places.google.test", "https://geocode.google.test",
                        Duration.ofMillis(400), Duration.ofSeconds(1)),
                new GoogleRateLimitProperties(9000, 9000, 9000, 60));

        assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

    @Test
    void TLS_연결이_멈추면_connect_한도에서_끊고_CONNECT로_남기고_한도를_돌려준다() throws IOException {
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            // TCP 연결은 받지만 TLS 핸드셰이크에 답하지 않는다.
            Thread acceptor = new Thread(() -> {
                List<Socket> held = new ArrayList<>();
                try {
                    while (true) {
                        held.add(silent.accept());
                    }
                } catch (IOException ignored) {
                    // 테스트가 끝나 소켓을 닫았다.
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            List<ILoggingEvent> logs = captureLogs(() -> contextRunner
                    .withPropertyValues("google.places.base-url=https://127.0.0.1:" + silent.getLocalPort(),
                            "google.places.api-key=test-key")
                    .run(context -> {
                        GooglePlacesClient client = context.getBean(GooglePlacesClient.class);

                        assertTimeoutPreemptively(Duration.ofMillis(1_000), () ->
                                assertThatThrownBy(() -> client.search(SearchQuery.from("경복"), "session-1"))
                                        .isInstanceOf(BusinessException.class));
                        verify(context.getBean(GoogleRequestLimiter.class)).refund(GoogleApi.AUTOCOMPLETE);
                    }));

            assertThat(timeoutPhases(logs)).containsExactly("CONNECT");
        }
    }

    @Test
    void 동시에_20번_불러도_풀에서_줄을_서지_않는다() {
        delayMillis = 300;
        contextRunner.withPropertyValues(properties()).run(context -> {
            GooglePlacesClient client = context.getBean(GooglePlacesClient.class);
            ExecutorService callers = Executors.newFixedThreadPool(20);
            try {
                long start = System.nanoTime();
                List<Future<?>> calls = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    calls.add(callers.submit(() -> client.search(SearchQuery.from("경복"), null)));
                }
                for (Future<?> call : calls) {
                    call.get();
                }

                // 기본 풀(경로당 5개)이면 5개씩 네 번 줄을 서 1.2초가 넘는다.
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(900));
            } finally {
                callers.shutdownNow();
            }
        });
    }

    @Test
    void 실제로_응답을_기다리다_끊기면_RESPONSE_타임아웃으로_남긴다() {
        delayMillis = 2_000;

        List<ILoggingEvent> logs = captureLogs(() -> contextRunner.withPropertyValues(properties()).run(context -> {
            GooglePlacesClient client = context.getBean(GooglePlacesClient.class);

            assertThatThrownBy(() -> client.search(SearchQuery.from("경복"), "session-1"))
                    .isInstanceOf(BusinessException.class);
            verify(context.getBean(GoogleRequestLimiter.class), never()).refund(GoogleApi.AUTOCOMPLETE);
        }));

        assertThat(timeoutPhases(logs)).containsExactly("RESPONSE");
    }

    @Test
    void 측정한_p99보다_느려도_1초_안이면_정상으로_받는다() {
        delayMillis = 600;
        contextRunner.withPropertyValues(properties()).run(context -> {
            GooglePlacesClient client = context.getBean(GooglePlacesClient.class);

            assertThat(client.search(SearchQuery.from("경복"), "session-1")).hasSize(1);
        });
    }

    private static List<ILoggingEvent> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleFailureTranslator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }

    private static List<String> timeoutPhases(List<ILoggingEvent> logs) {
        return logs.stream()
                .filter(event -> "GOOGLE_PLACES_TIMEOUT".equals(field(event, "event")))
                .map(event -> field(event, "phase"))
                .toList();
    }

    private static String field(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() == null) {
            return null;
        }
        return event.getKeyValuePairs().stream()
                .filter(pair -> key.equals(pair.key))
                .map(pair -> String.valueOf(pair.value))
                .findFirst()
                .orElse(null);
    }

    private String[] properties() {
        return new String[] {
                "google.places.base-url=http://127.0.0.1:" + google.getAddress().getPort(),
                "google.places.api-key=test-key"
        };
    }
}
