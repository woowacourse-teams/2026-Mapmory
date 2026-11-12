package com.mapmory.backend.place.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.dropoff.DropOffLog.Outcome;
import com.mapmory.backend.dropoff.DropOffLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 회원 한도에 막혀 컨트롤러에 들어가지 못한 요청도 막힌 지점 기록에 남는다. */
class PlaceRateLimitLookupLogIntegrationTest extends IntegrationTest {

    private static final String IOS = "Mapmory/1.2.7 CFNetwork/1568 Darwin/24.0.0";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DropOffLogRepository repository;

    @Autowired
    private PlaceRateLimitProperties rateLimitProperties;

    @Test
    void 회원_한도에_막힌_검색도_검색어와_함께_남긴다() throws Exception {
        willReturn(List.of()).given(googlePlacesClient).search(any(), anyString());
        String accessToken = guestAccessToken();
        int limit = rateLimitProperties.searchesPerMemberPerMinute();
        for (int i = 0; i < limit; i++) {
            search(accessToken, "도쿄").andExpect(status().isOk());
        }

        search(accessToken, "도쿄").andExpect(status().isTooManyRequests());

        assertThat(repository.findAll()).filteredOn(log -> log.getOutcome() == Outcome.FAILED).singleElement()
                .satisfies(log -> {
                    assertThat(log.getErrorCode()).isEqualTo("PLACE_RATE_LIMITED");
                    assertThat(log.getQuery()).isEqualTo("도쿄");
                    assertThat(log.getUserAgent()).isEqualTo(IOS);
                });
    }

    @Test
    void 같은_한도를_쓰는_사진_좌표_요청은_장소_선택으로_남기지_않는다() throws Exception {
        String accessToken = guestAccessToken();
        int limit = rateLimitProperties.selectionsPerMemberPerDay();
        for (int i = 0; i < limit; i++) {
            mockMvc.perform(post("/api/v1/places/location")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType("application/json").content("{}"));
        }

        mockMvc.perform(post("/api/v1/places/location")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests());

        assertThat(repository.findAll()).isEmpty();
    }

    private ResultActions search(String accessToken, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/places/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.USER_AGENT, IOS)
                .param("query", query)
                .param("sessionToken", "session-1"));
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
