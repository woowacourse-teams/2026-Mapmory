package com.mapmory.backend.place.infrastructure.google;

import static com.mapmory.backend.support.LogEvents.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleReverseGeocodeTest {

    private static final String PLACES_URL = "https://places.google.test";
    private static final String GEOCODING_URL = "https://maps.google.test";
    private static final Coordinate DAEGU_JUNG_GU = new Coordinate(35.8694, 128.6062);
    private static final GoogleCircuitBreakerProperties BREAKER =
            new GoogleCircuitBreakerProperties(Duration.ofSeconds(60), 5, 50, Duration.ofSeconds(60), 3);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleRequestLimiter limiter = mock(GoogleRequestLimiter.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
    private final CircuitBreaker breaker = GoogleCircuitBreakerConfig.circuitBreaker(BREAKER, clock);
    private final GooglePlacesClient client = new GooglePlacesClient(builder, PLACES_URL, GEOCODING_URL, "test-key",
            limiter, breaker);

    @Test
    void 좌표를_한국어_주소_구성요소로_바꾼다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL + "/maps/api/geocode/json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latlng", "35.869400,128.606200"))
                .andExpect(queryParam("language", "ko"))
                .andExpect(queryParam("key", "test-key"))
                .andRespond(withSuccess("""
                        {"status":"OK","results":[
                          {"types":["plus_code"],"address_components":[
                            {"long_name":"VJ94+QF","short_name":"VJ94+QF","types":["plus_code"]}]},
                          {"types":["street_address"],"address_components":[
                            {"long_name":"동성로","short_name":"동성로","types":["political","sublocality","sublocality_level_2"]},
                            {"long_name":"중구","short_name":"중구","types":["political","sublocality","sublocality_level_1"]},
                            {"long_name":"대구광역시","short_name":"대구광역시","types":["administrative_area_level_1","political"]},
                            {"long_name":"대한민국","short_name":"KR","types":["country","political"]}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU))
                .contains(new RegionAddress("KR", "대구광역시", List.of("중구")));
        server.verify();
        verify(limiter).reserveGeocode();
    }

    @Test
    void 경기도_시_구까지_큰_단위부터_넘긴다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"status":"OK","results":[{"address_components":[
                          {"long_name":"분당구","short_name":"분당구","types":["political","sublocality","sublocality_level_1"]},
                          {"long_name":"성남시","short_name":"성남시","types":["locality","political"]},
                          {"long_name":"경기도","short_name":"경기도","types":["administrative_area_level_1","political"]},
                          {"long_name":"대한민국","short_name":"KR","types":["country","political"]}]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU))
                .contains(new RegionAddress("KR", "경기도", List.of("성남시", "분당구")));
    }

    @Test
    void 주소가_없는_좌표는_빈_값이다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"status":"ZERO_RESULTS","results":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU)).isEqualTo(Optional.empty());
    }

    @Test
    void 키가_거절되면_ERROR로_알리고_제공자_장애로_답한다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"status":"REQUEST_DENIED","error_message":"This API project is not authorized to use this API."}
                        """, MediaType.APPLICATION_JSON));

        List<ILoggingEvent> logs = captureLogs(() -> assertThatThrownBy(() -> client.findAddress(DAEGU_JUNG_GU))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE)));

        assertThat(logs).extracting(event -> field(event, "event") + ":" + event.getLevel())
                .containsExactly("GOOGLE_GEOCODING_REJECTED:ERROR");
        assertThat(logs).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain("35.86"));
        // Google이 사용량에 세지 않는 거절이라, 키를 고칠 때까지 월 한도가 새지 않게 돌려준다.
        verify(limiter).refundGeocode();
    }

    @Test
    void 구글_일시_오류는_서킷_실패로_세고_월_한도를_돌려준다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"status":"UNKNOWN_ERROR","results":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findAddress(DAEGU_JUNG_GU))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE));
        verify(limiter).refundGeocode();
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void 국가가_없는_결과만_있으면_지역_이름이_있는_첫_결과를_쓴다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL)))
                .andRespond(withSuccess("""
                        {"status":"OK","results":[
                          {"types":["plus_code"],"address_components":[
                            {"long_name":"XXXX+XX","short_name":"XXXX+XX","types":["plus_code"]}]},
                          {"types":["political"],"address_components":[
                            {"long_name":"울릉군","short_name":"울릉군","types":["administrative_area_level_2","political"]},
                            {"long_name":"경상북도","short_name":"경상북도","types":["administrative_area_level_1","political"]}]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findAddress(DAEGU_JUNG_GU))
                .contains(new RegionAddress(null, "경상북도", List.of("울릉군")));
    }

    @Test
    void 구글_5xx는_월_한도를_돌려준다() {
        server.expect(requestTo(Matchers.startsWith(GEOCODING_URL))).andRespond(withServerError());

        assertThatThrownBy(() -> client.findAddress(DAEGU_JUNG_GU)).isInstanceOf(BusinessException.class);
        verify(limiter).refundGeocode();
    }

    private static List<ILoggingEvent> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(GooglePlacesClient.class);
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
