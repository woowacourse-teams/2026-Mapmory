package com.mapmory.backend.place.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GooglePlacesClientTest {

    private static final String BASE_URL = "https://places.google.test";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleRequestLimiter limiter = mock(GoogleRequestLimiter.class);
    private final GooglePlacesClient client = new GooglePlacesClient(builder, BASE_URL, "test-key", limiter);

    @Test
    void 자동완성_후보를_장소명과_보조_주소로_반환한다() {
        server.expect(requestTo(BASE_URL + "/v1/places:autocomplete"))
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
                .isEqualTo(new PlaceDetails("ChIJ-gyeong", null, "KR", null, null,
                        GooglePlacesClient.ATTRIBUTION, GooglePlacesClient.ATTRIBUTION_URL,
                        List.of("서울특별시", "종로구")));
        server.verify();
    }

    @Test
    void 저장용_조회는_Essentials_필드만_요청해_이름과_좌표를_받지_않는다() {
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
        assertThat(place.latitude()).isNull();
        assertThat(place.longitude()).isNull();
        assertThat(place.countryCode()).isEqualTo("KR");
        assertThat(place.addressAreas()).containsExactly("경기도", "성남시", "분당구");
        server.verify();
        verify(limiter).reserveDetails();
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

        assertThatThrownBy(() -> client.findById("ChIJ-missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("PLACE_NOT_FOUND"));
    }

    @Test
    void 잘못된_API_키로_거절되면_장소_없음이_아니라_제공자_장애로_처리한다() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error":{"code":400,"status":"INVALID_ARGUMENT",
                          "details":[{"reason":"API_KEY_INVALID"}]}}
                        """));

        assertThatThrownBy(() -> client.findById("ChIJ-gyeong"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("PLACE_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void 검색_요청이_거절되면_제공자_장애로_처리한다() {
        server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.search("경복", "session-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("PLACE_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void 하루_한도를_넘으면_Google을_호출하지_않는다() {
        doThrow(new BusinessException(PlaceErrorCode.PLACE_SEARCH_BUDGET_EXHAUSTED))
                .when(limiter).reserveAutocomplete();

        assertThatThrownBy(() -> client.search("경복", "session-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("PLACE_SEARCH_BUDGET_EXHAUSTED"));
        server.verify();
    }

    @Test
    void API_키가_없으면_외부_요청_없이_오류를_반환한다() {
        GooglePlacesClient unconfigured =
                new GooglePlacesClient(RestClient.builder(), BASE_URL, "", mock(GoogleRequestLimiter.class));

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.search("경복", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("PLACE_PROVIDER_UNAVAILABLE"));
    }
}
