package com.mapmory.backend.place.infrastructure.geoapify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeoapifyClientTest {

    @Test
    void 검색_결과에서_장소_ID와_이름을_반환한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoapifyClient client = new GeoapifyClient(
                builder, "https://api.geoapify.test", "test-key", mock(GeoapifyRequestLimiter.class));
        assertThat(client.providerCode()).isEqualTo("GEOAPIFY");
        server.expect(queryParam("text", URLEncoder.encode("한강공원", StandardCharsets.UTF_8)))
                .andRespond(withSuccess("""
                        {"results":[
                          {"place_id":"park-1","name":"여의도한강공원","formatted":"서울 영등포구"},
                          {"place_id":"park-2","name":"뚝섬한강공원","formatted":"서울 광진구"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.search("한강공원"))
                .containsExactly(
                        new PlaceCandidate("park-1", "여의도한강공원", "서울 영등포구",
                                PlaceAttribution.OSM_TEXT, PlaceAttribution.OSM_URL),
                        new PlaceCandidate("park-2", "뚝섬한강공원", "서울 광진구",
                                PlaceAttribution.OSM_TEXT, PlaceAttribution.OSM_URL)
                );
        server.verify();
    }

    @Test
    void 선택한_장소는_ID로_다시_조회한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoapifyClient client = new GeoapifyClient(
                builder, "https://api.geoapify.test", "test-key", mock(GeoapifyRequestLimiter.class));
        server.expect(queryParam("id", "park-1"))
                .andRespond(withSuccess("""
                        {"features":[{"properties":{
                          "feature_type":"details","name":"여의도한강공원",
                          "country_code":"kr","lat":37.528,"lon":126.932,
                          "datasource":{"attribution":"© OpenStreetMap contributors",
                                        "url":"https://www.openstreetmap.org/copyright"}
                        }}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findById("park-1"))
                .isEqualTo(new PlaceDetails("park-1", "여의도한강공원", "KR", 37.528, 126.932,
                        PlaceAttribution.OSM_TEXT, PlaceAttribution.OSM_URL));
        server.verify();
    }

    @Test
    void API_키가_없으면_외부_요청_없이_오류를_반환한다() {
        GeoapifyClient client = new GeoapifyClient(
                RestClient.builder(), "https://api.geoapify.test", "", mock(GeoapifyRequestLimiter.class));

        assertThatThrownBy(() -> client.search("한강공원"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("PLACE_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void 외부_API_오류에_키가_들어간_예외를_노출하지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoapifyClient client = new GeoapifyClient(
                builder, "https://api.geoapify.test", "secret-key", mock(GeoapifyRequestLimiter.class));
        server.expect(queryParam("apiKey", "secret-key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.search("한강공원"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    assertThat(((BusinessException) error).getErrorCode().code())
                            .isEqualTo("PLACE_PROVIDER_UNAVAILABLE");
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getMessage()).doesNotContain("secret-key");
                });
        server.verify();
    }

    @Test
    void 한도에_걸리면_외부_API를_호출하지_않는다() {
        GeoapifyRequestLimiter limiter = mock(GeoapifyRequestLimiter.class);
        doThrow(new BusinessException(PlaceErrorCode.PLACE_RATE_LIMITED))
                .when(limiter).reserveSearch();
        GeoapifyClient client = new GeoapifyClient(
                RestClient.builder(), "https://api.geoapify.test", "test-key", limiter);

        assertThatThrownBy(() -> client.search("한강공원"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("PLACE_RATE_LIMITED"));
    }
}
