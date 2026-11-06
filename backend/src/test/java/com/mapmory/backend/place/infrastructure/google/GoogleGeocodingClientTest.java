package com.mapmory.backend.place.infrastructure.google;

import tools.jackson.databind.node.JsonNodeFactory;

import static org.mockito.Mockito.never;

import static com.mapmory.backend.support.LogEvents.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.domain.Coordinate;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import com.mapmory.backend.region.domain.RegionAddress;
import com.mapmory.backend.support.MutableClock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleGeocodingClientTest {

    private static final String GEOCODING_URL = "https://geocode.google.test";
    private static final Coordinate DAEGU_JUNG_GU = new Coordinate(35.8694, 128.6062);
    private static final GoogleCircuitBreakerProperties BREAKER =
            new GoogleCircuitBreakerProperties(Duration.ofSeconds(60), 5, 50, Duration.ofSeconds(60), 3);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleRequestLimiter limiter = mock(GoogleRequestLimiter.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
    private final CircuitBreaker breaker =
            GoogleCircuitBreakerConfig.circuitBreaker(BREAKER, clock, GoogleApi.Host.GEOCODING);
    private final CircuitBreaker placesBreaker =
            GoogleCircuitBreakerConfig.circuitBreaker(BREAKER, clock, GoogleApi.Host.PLACES);
    private final GoogleCallGuard guard = new GoogleCallGuard(
            GoogleCircuitBreakers.of(host -> host == GoogleApi.Host.GEOCODING ? breaker : placesBreaker), limiter);
    private final GoogleGeocodingClient client = new GoogleGeocodingClient(builder.baseUrl(GEOCODING_URL).build(),
            new GoogleApiProperties("test-key", "https://places.google.test", GEOCODING_URL,
                    Duration.ofMillis(400), Duration.ofSeconds(1)),
            guard);

    @Test
    void 키는_헤더로_보내고_좌표를_한국어_주소_구성요소로_바꾼다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL + "/v4/geocode/location?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("location.latitude", "35.869400"))
                .andExpect(queryParam("location.longitude", "128.606200"))
                .andExpect(queryParam("languageCode", "ko"))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", "results.addressComponents"))
                // 키가 URL에 실리면 프록시·접근 로그 어디에든 남을 수 있다.
                .andExpect(requestTo(Matchers.not(Matchers.containsString("test-key"))))
                .andRespond(withSuccess("""
                        {"results":[
                          {"addressComponents":[
                            {"longText":"VJ94+QF","shortText":"VJ94+QF","types":["plus_code"]}]},
                          {"addressComponents":[
                            {"longText":"동성로","shortText":"동성로","types":["political","sublocality","sublocality_level_2"]},
                            {"longText":"중구","shortText":"중구","types":["political","sublocality","sublocality_level_1"]},
                            {"longText":"대구광역시","shortText":"대구광역시","types":["administrative_area_level_1","political"]},
                            {"longText":"대한민국","shortText":"KR","types":["country","political"]}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU))
                .contains(new RegionAddress("KR", "대구광역시", List.of("중구")));
        server.verify();
        verify(limiter).reserve(GoogleApi.GEOCODE);
    }

    @Test
    void 경기도_시_구까지_큰_단위부터_넘긴다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"results":[{"addressComponents":[
                          {"longText":"분당구","shortText":"분당구","types":["political","sublocality","sublocality_level_1"]},
                          {"longText":"성남시","shortText":"성남시","types":["locality","political"]},
                          {"longText":"경기도","shortText":"경기도","types":["administrative_area_level_1","political"]},
                          {"longText":"대한민국","shortText":"KR","types":["country","political"]}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU))
                .contains(new RegionAddress("KR", "경기도", List.of("성남시", "분당구")));
    }

    @Test
    void 주소가_없는_좌표는_빈_값이다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU)).isEqualTo(Optional.empty());
    }

    @Test
    void 키가_거절되면_한도를_돌려주고_ERROR로_알린다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"code":403,"status":"PERMISSION_DENIED","message":"Geocoding API has not been used"}}
                        """));

        List<ILoggingEvent> logs = captureLogs(() -> assertThatThrownBy(() -> client.findAddress(DAEGU_JUNG_GU))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE)));

        assertThat(logs).extracting(event -> field(event, "event") + ":" + event.getLevel())
                .containsExactly("GOOGLE_GEOCODING_REJECTED:ERROR");
        // 좌표 요청이라 Google이 준 거절 사유(응답 본문)도 남기지 않는다.
        assertThat(logs).allSatisfy(event -> assertThat(rendered(event)).doesNotContain("has not been used"));
        // 좌표와 키는 로그 어디에도(원인 예외 포함) 남기지 않는다.
        assertThat(logs).allSatisfy(event -> assertThat(rendered(event))
                .doesNotContain("35.86", "128.60", "test-key"));
        // Geocoding은 성공한 요청만 과금한다. 키를 고칠 때까지 월 한도가 새지 않게 돌려준다.
        verify(limiter).refund(GoogleApi.GEOCODE);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void 국가가_없는_결과만_있으면_지역_이름이_있는_첫_결과를_쓴다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"results":[
                          {"addressComponents":[
                            {"longText":"XXXX+XX","shortText":"XXXX+XX","types":["plus_code"]}]},
                          {"addressComponents":[
                            {"longText":"울릉군","shortText":"울릉군","types":["administrative_area_level_2","political"]},
                            {"longText":"경상북도","shortText":"경상북도","types":["administrative_area_level_1","political"]}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU))
                .contains(new RegionAddress(null, "경상북도", List.of("울릉군")));
    }

    @Test
    void 국가만_있는_결과보다_국가가_없어도_시도가_있는_결과를_쓴다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"results":[
                          {"addressComponents":[
                            {"longText":"XXXX+XX","shortText":"XXXX+XX","types":["plus_code"]},
                            {"longText":"대한민국","shortText":"KR","types":["country","political"]}]},
                          {"addressComponents":[
                            {"longText":"울릉군","shortText":"울릉군","types":["administrative_area_level_2","political"]},
                            {"longText":"경상북도","shortText":"경상북도","types":["administrative_area_level_1","political"]}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU))
                .contains(new RegionAddress(null, "경상북도", List.of("울릉군")));
    }

    @Test
    void 시도가_없는_앞쪽_결과보다_시도까지_있는_결과를_쓴다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"results":[
                          {"addressComponents":[
                            {"longText":"VJ94+QF","shortText":"VJ94+QF","types":["plus_code"]},
                            {"longText":"대한민국","shortText":"KR","types":["country","political"]}]},
                          {"addressComponents":[
                            {"longText":"봉화군","shortText":"봉화군","types":["administrative_area_level_2","political"]},
                            {"longText":"경상북도","shortText":"경상북도","types":["administrative_area_level_1","political"]},
                            {"longText":"대한민국","shortText":"KR","types":["country","political"]}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU))
                .contains(new RegionAddress("KR", "경상북도", List.of("봉화군")));
    }

    @Test
    void 구글_5xx는_서킷_실패로_세고_월_한도를_돌려준다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL))).andRespond(withServerError());

        assertThatThrownBy(() -> client.findAddress(DAEGU_JUNG_GU)).isInstanceOf(BusinessException.class);
        verify(limiter).refund(GoogleApi.GEOCODE);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void 지오코딩_서킷이_열리면_한도를_쓰지_않고_바로_실패하고_장소_호출은_막지_않는다() {
        breaker.transitionToOpenState();

        assertThatThrownBy(() -> client.findAddress(DAEGU_JUNG_GU))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE));
        verify(limiter, never()).reserve(GoogleApi.GEOCODE);

        assertThat(guard.call(GoogleApi.AUTOCOMPLETE, () -> JsonNodeFactory.instance.objectNode())).isNotNull();
        verify(limiter).reserve(GoogleApi.AUTOCOMPLETE);
        assertThat(placesBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static String rendered(ILoggingEvent event) {
        StringBuilder text = new StringBuilder(event.getFormattedMessage());
        for (var cause = event.getThrowableProxy(); cause != null; cause = cause.getCause()) {
            text.append(' ').append(cause.getMessage());
        }
        return text.toString();
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
}
