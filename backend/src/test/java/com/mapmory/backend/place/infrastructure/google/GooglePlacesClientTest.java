package com.mapmory.backend.place.infrastructure.google;

import static com.mapmory.backend.support.LogEvents.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.support.MutableClock;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GooglePlacesClientTest {

    private static final String BASE_URL = "https://places.google.test";
    private static final String AUTOCOMPLETE_URL = BASE_URL + "/v1/places:autocomplete";
    private static final String CANDIDATES = """
            {"suggestions":[{"placePrediction":{"placeId":"ChIJ-gyeong","text":{"text":"경복궁"}}}]}
            """;
    // application.yaml의 google.places.circuit-breaker와 같은 값이다.
    private static final GoogleCircuitBreakerProperties BREAKER =
            new GoogleCircuitBreakerProperties(Duration.ofSeconds(60), 5, 50, Duration.ofSeconds(60), 3);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleRequestLimiter limiter = mock(GoogleRequestLimiter.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-14T00:00:00Z"));
    private final GooglePlacesClient client = new GooglePlacesClient(builder, BASE_URL, "test-key", limiter,
            GoogleCircuitBreakerConfig.circuitBreaker(BREAKER, clock));

    @Test
    void 자동완성_후보를_장소명과_보조_주소로_반환한다() {
        server.expect(requestTo(AUTOCOMPLETE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(content().json("""
                        {"input":"경복","languageCode":"ko","sessionToken":"session-1"}
                        """))
                .andRespond(withSuccess("""
                        {"suggestions":[
                          {"placePrediction":{"placeId":"ChIJ-gyeong","text":{"text":"경복궁, 서울특별시 종로구"},
                            "structuredFormat":{"mainText":{"text":"경복궁"},
                                                "secondaryText":{"text":"서울특별시 종로구"}}}},
                          {"queryPrediction":{"text":{"text":"경복궁 맛집"}}},
                          {"placePrediction":{"placeId":"ChIJ-station","text":{"text":"경복궁역"}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.search("경복", "session-1")).containsExactly(
                new PlaceCandidate("ChIJ-gyeong", "경복궁", "서울특별시 종로구",
                        GooglePlacesClient.ATTRIBUTION, GooglePlacesClient.ATTRIBUTION_URL),
                new PlaceCandidate("ChIJ-station", "경복궁역", null,
                        GooglePlacesClient.ATTRIBUTION, GooglePlacesClient.ATTRIBUTION_URL));
        server.verify();
        verify(limiter).reserveAutocomplete();
    }

    @Test
    void 선택_조회는_같은_세션_토큰으로_주소_구성요소만_받는다() {
        server.expect(requestToUriTemplate(BASE_URL + "/v1/places/{id}?languageCode=ko&sessionToken=session-1",
                        "ChIJ-gyeong"))
                .andExpect(header("X-Goog-FieldMask", "id,addressComponents"))
                .andRespond(withSuccess("""
                        {"id":"ChIJ-gyeong",
                         "addressComponents":[
                           {"longText":"세종로","shortText":"세종로","types":["sublocality_level_2","sublocality","political"]},
                           {"longText":"종로구","shortText":"종로구","types":["sublocality_level_1","sublocality","political"]},
                           {"longText":"서울특별시","shortText":"서울특별시","types":["administrative_area_level_1","political"]},
                           {"longText":"대한민국","shortText":"KR","types":["country","political"]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findForSelection("ChIJ-gyeong", "session-1"))
                .isEqualTo(new PlaceDetails("ChIJ-gyeong", null, "KR",
                        GooglePlacesClient.ATTRIBUTION, GooglePlacesClient.ATTRIBUTION_URL,
                        List.of("서울특별시", "종로구")));
        server.verify();
    }

    @Test
    void 지역을_정하는_조회는_Essentials_필드만_요청해_이름과_좌표를_받지_않는다() {
        server.expect(requestToUriTemplate(BASE_URL + "/v1/places/{id}?languageCode=ko", "ChIJ-bundang"))
                .andExpect(header("X-Goog-FieldMask", "id,addressComponents"))
                .andRespond(withSuccess("""
                        {"id":"ChIJ-bundang","addressComponents":[
                           {"longText":"분당구","types":["sublocality_level_1","sublocality","political"]},
                           {"longText":"성남시","types":["locality","political"]},
                           {"longText":"경기도","types":["administrative_area_level_1","political"]},
                           {"longText":"대한민국","shortText":"KR","types":["country","political"]}]}
                        """, MediaType.APPLICATION_JSON));

        PlaceDetails place = client.findById("ChIJ-bundang");

        assertThat(place.name()).isNull();
        assertThat(place.countryCode()).isEqualTo("KR");
        assertThat(place.addressAreas()).containsExactly("경기도", "성남시", "분당구");
        server.verify();
        verify(limiter).reserveDetails();
    }

    @Test
    void 기록에_붙일_장소는_Google을_부르지_않고_출처_표기와_함께_만든다() {
        PlaceDetails place = client.findForRecord("ChIJ-bundang");

        assertThat(place.placeId()).isEqualTo("ChIJ-bundang");
        assertThat(place.name()).isNull();
        assertThat(place.countryCode()).isNull();
        assertThat(place.attribution()).isEqualTo("Google Maps");
        assertThat(place.attributionUrl()).isEqualTo("https://www.google.com/maps");
        server.verify();
        verifyNoInteractions(limiter);
    }

    @Test
    void 기록에_붙일_장소_ID_형식이_틀리면_거절한다() {
        assertError(() -> client.findForRecord("../places"), "INVALID_PLACE_ID");
    }

    @Test
    void API_키를_뺀_뒤에는_Google_장소를_기록에_붙이지_않는다() {
        assertError(() -> unconfiguredClient().findForRecord("ChIJ-bundang"), "PLACE_PROVIDER_UNAVAILABLE");
    }

    @Test
    void 괌은_Google이_준_국가_코드_GU로_돌려준다() {
        server.expect(queryParam("languageCode", "ko"))
                .andRespond(withSuccess("""
                        {"id":"ChIJ-tumon","addressComponents":[{"shortText":"GU","types":["country","political"]}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findById("ChIJ-tumon").countryCode()).isEqualTo("GU");
    }

    @Test
    void 없는_장소_ID는_장소_없음으로_처리한다() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertError(() -> client.findById("ChIJ-missing"), "PLACE_NOT_FOUND");
    }

    @Test
    void 잘못된_API_키로_거절되면_장소_없음이_아니라_제공자_장애로_처리한다() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error":{"code":400,"status":"INVALID_ARGUMENT",
                          "details":[{"reason":"API_KEY_INVALID"}]}}
                        """));

        assertError(() -> client.findById("ChIJ-gyeong"), "PLACE_PROVIDER_UNAVAILABLE");
    }

    @Test
    void 검색_요청이_거절되면_제공자_장애로_처리한다() {
        server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertError(() -> client.search("경복", "session-1"), "PLACE_PROVIDER_UNAVAILABLE");
    }

    @Test
    void 월_한도를_넘으면_Google을_호출하지_않는다() {
        doThrow(new BusinessException(PlaceErrorCode.PLACE_SEARCH_BUDGET_EXHAUSTED))
                .when(limiter).reserveAutocomplete();

        assertError(() -> client.search("경복", "session-1"), "PLACE_SEARCH_BUDGET_EXHAUSTED");
        server.verify();
    }

    @Test
    void API_키가_없으면_외부_요청_없이_오류를_반환한다() {
        assertError(() -> unconfiguredClient().search("경복", "session-1"), "PLACE_PROVIDER_UNAVAILABLE");
    }

    // MockRestServiceServer는 요청을 받은 뒤에 응답을 추가할 수 없어, Google이 줄 응답을 먼저 모두 정해 둔다.
    @Nested
    class Google_장애가_이어지면 {

        @Test
        void 최근_60초_동안_5번_이상_불러_절반_이상_5xx면_Google도_한도도_건드리지_않고_바로_실패한다() {
            expectServerErrors(5);
            searchFails(5);

            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");

            verify(limiter, times(5)).reserveAutocomplete();
            verify(limiter, times(5)).refundAutocomplete();
        }

        @Test
        void 타임아웃과_연결_오류도_실패로_센다() {
            server.expect(ExpectedCount.times(3), requestTo(AUTOCOMPLETE_URL))
                    .andRespond(withException(new HttpTimeoutException("Request cancelled")));
            server.expect(ExpectedCount.times(2), requestTo(AUTOCOMPLETE_URL))
                    .andRespond(withException(new ConnectException("Connection refused")));
            searchFails(5);

            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");

            server.verify();
            verify(limiter, times(5)).reserveAutocomplete();
        }

        @Test
        void Google이_4xx로_답한_호출은_실패로_세지_않는다() {
            server.expect(ExpectedCount.times(5), requestTo(AUTOCOMPLETE_URL))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));
            expectCandidates(1);
            searchFails(5);

            assertThat(client.search("경복", null)).hasSize(1);
            server.verify();
        }

        // 한도 거절을 성공으로 세면 5번 실패가 11번 중 5번(45%)이 돼 회로가 열리지 않는다.
        @Test
        void 우리_한도_거절은_세지_않아_Google_실패율을_낮추지_않는다() {
            BusinessException rateLimited = new BusinessException(PlaceErrorCode.PLACE_RATE_LIMITED);
            doThrow(rateLimited, rateLimited, rateLimited, rateLimited, rateLimited, rateLimited)
                    .doNothing()
                    .when(limiter).reserveAutocomplete();
            expectServerErrors(5);
            for (int i = 0; i < 6; i++) {
                assertError(() -> client.search("경복", null), "PLACE_RATE_LIMITED");
            }
            searchFails(5);

            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");

            verify(limiter, times(11)).reserveAutocomplete();
        }

        @Test
        void 열린_뒤_60초가_지나면_다시_시험해_Google이_돌아왔으면_닫는다() {
            expectServerErrors(5);
            expectCandidates(4);
            searchFails(5);

            clock.advance(Duration.ofSeconds(60));
            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");

            clock.advance(Duration.ofSeconds(1));
            for (int i = 0; i < 4; i++) {
                assertThat(client.search("경복", null)).hasSize(1);
            }
            server.verify();
        }

        @Test
        void 시험_3번_중_절반_이상_실패하면_다시_연다() {
            expectServerErrors(5);
            expectCandidates(1);
            expectServerErrors(2);
            searchFails(5);
            clock.advance(Duration.ofSeconds(61));

            assertThat(client.search("경복", null)).hasSize(1);
            searchFails(2);

            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");
            server.verify();
        }

        // 운영 알람은 ERROR 로그를 Discord로 보낸다. 장애 동안 60초마다 다시 열려도 알림은 장애 하나에 한 번이다.
        @Test
        void 닫혀_있다_열릴_때만_ERROR로_알리고_나머지_상태_변화는_WARN으로_남긴다() {
            expectServerErrors(5);
            expectServerErrors(3);
            expectCandidates(3);
            Logger logger = (Logger) LoggerFactory.getLogger(GoogleCircuitBreakerConfig.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                searchFails(5);
                clock.advance(Duration.ofSeconds(61));
                searchFails(3);
                clock.advance(Duration.ofSeconds(61));
                for (int i = 0; i < 3; i++) {
                    client.search("경복", null);
                }
            } finally {
                logger.detachAppender(appender);
            }

            assertThat(appender.list).extracting(event -> field(event, "transition") + ":" + event.getLevel())
                    .containsExactly("CLOSED_TO_OPEN:ERROR", "OPEN_TO_HALF_OPEN:WARN", "HALF_OPEN_TO_OPEN:WARN",
                            "OPEN_TO_HALF_OPEN:WARN", "HALF_OPEN_TO_CLOSED:WARN");
        }

        private void expectServerErrors(int count) {
            server.expect(ExpectedCount.times(count), requestTo(AUTOCOMPLETE_URL)).andRespond(withServerError());
        }

        private void expectCandidates(int count) {
            server.expect(ExpectedCount.times(count), requestTo(AUTOCOMPLETE_URL))
                    .andRespond(withSuccess(CANDIDATES, MediaType.APPLICATION_JSON));
        }
    }

    @Nested
    class 월_한도_환불 {

        @Test
        void Google이_5xx로_답하면_쓴_월_한도를_돌려준다() {
            server.expect(method(HttpMethod.POST)).andRespond(withServerError());
            server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");
            assertError(() -> client.findById("ChIJ-gyeong"), "PLACE_PROVIDER_UNAVAILABLE");

            verify(limiter).refundAutocomplete();
            verify(limiter).refundDetails();
        }

        // 연결하지 못한 요청은 Google에 닿지 않아 과금되지 않는다. 시험 호출이 연결을 못 할 때도 여기에 해당한다.
        // 예외는 실제 클라이언트(JDK HttpClient)가 연결 거부, 2초 연결 타임아웃, 없는 호스트에서 던지는 것과 같다.
        @Test
        void 연결조차_못_했으면_돌려준다() {
            server.expect(method(HttpMethod.POST)).andRespond(withException(new ConnectException()));
            server.expect(method(HttpMethod.POST))
                    .andRespond(withException(new HttpConnectTimeoutException("HTTP connect timed out")));
            server.expect(method(HttpMethod.GET)).andRespond(withException(new ConnectException()));

            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");
            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");
            assertError(() -> client.findById("ChIJ-gyeong"), "PLACE_PROVIDER_UNAVAILABLE");

            verify(limiter, times(2)).refundAutocomplete();
            verify(limiter).refundDetails();
        }

        // 연결한 뒤 3초 안에 답이 없으면 Google이 처리했는지 알 수 없고, 4xx는 Google이 처리해 과금될 수 있다.
        @Test
        void 연결한_뒤_답을_기다리다_끊겼거나_4xx면_돌려주지_않는다() {
            server.expect(method(HttpMethod.POST))
                    .andRespond(withException(new HttpTimeoutException("Request cancelled")));
            server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
            server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");
            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");
            assertError(() -> client.findById("ChIJ-missing"), "PLACE_NOT_FOUND");

            verify(limiter, never()).refundAutocomplete();
            verify(limiter, never()).refundDetails();
        }
    }

    private void searchFails(int count) {
        for (int i = 0; i < count; i++) {
            assertError(() -> client.search("경복", null), "PLACE_PROVIDER_UNAVAILABLE");
        }
    }

    private GooglePlacesClient unconfiguredClient() {
        return new GooglePlacesClient(RestClient.builder(), BASE_URL, "", mock(GoogleRequestLimiter.class),
                GoogleCircuitBreakerConfig.circuitBreaker(BREAKER, clock));
    }

    private static void assertError(ThrowingCallable action, String expectedCode) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo(expectedCode));
    }
}
