package com.mapmory.backend.place.ratelimit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.place.application.model.PlaceDetails;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 한도는 운영 설정값 그대로 채운다. 테스트만 한도를 낮추면 이 테스트를 위한 컨텍스트를 하나 더 띄우게 된다.
 */
class PlaceRateLimitInterceptorIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaceRateLimitProperties properties;

    @Test
    void 회원별_검색_한도를_넘으면_Google을_부르지_않고_거절한다() throws Exception {
        willReturn(List.of()).given(googlePlacesClient).search(anyString(), anyString());
        String accessToken = guestAccessToken();
        int limit = properties.searchesPerMemberPerMinute();

        for (int i = 0; i < limit; i++) {
            search(accessToken).andExpect(status().isOk());
        }
        search(accessToken)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PLACE_RATE_LIMITED"));

        verify(googlePlacesClient, times(limit)).search(anyString(), anyString());
        // 다른 회원은 따로 센다.
        search(guestAccessToken()).andExpect(status().isOk());
    }

    @Test
    void 선택_한도는_검색_한도와_따로_센다() throws Exception {
        willReturn(List.of()).given(googlePlacesClient).search(anyString(), anyString());
        willReturn(new PlaceDetails("ChIJ-tokyo", null, null, "Google Maps", "https://www.google.com/maps", List.of()))
                .given(googlePlacesClient).findForSelection(anyString(), any());
        String accessToken = guestAccessToken();
        int limit = properties.selectionsPerMemberPerDay();

        for (int i = 0; i < limit; i++) {
            select(accessToken).andExpect(status().isOk());
        }
        select(accessToken)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PLACE_RATE_LIMITED"));

        verify(googlePlacesClient, times(limit)).findForSelection(anyString(), any());
        // 선택 한도를 다 써도 검색 한도는 그대로 남아 있다. 선택 요청이 검색 한도를 쓰지 않았다.
        for (int i = 0; i < properties.searchesPerMemberPerMinute(); i++) {
            search(accessToken).andExpect(status().isOk());
        }
    }

    private ResultActions search(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/places/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .param("query", "도쿄")
                .param("sessionToken", "session-1"));
    }

    private ResultActions select(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/places/ChIJ-tokyo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
