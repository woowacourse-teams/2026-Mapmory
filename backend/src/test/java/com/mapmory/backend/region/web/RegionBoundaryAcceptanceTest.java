package com.mapmory.backend.region.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 앱 지도에 없던 홍콩·괌 같은 지역도 서버 지역 목록 기준으로 경계를 받는지 확인한다.
 */
class RegionBoundaryAcceptanceTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 로그인한_사용자는_홍콩_경계를_받는다() throws Exception {
        mockMvc.perform(get("/api/v1/regions/HK/boundary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionCode").value("HK"))
                .andExpect(jsonPath("$.data.rings[0][0].length()").value(2));
    }

    @Test
    void 저장한_경계가_최신이면_304를_받는다() throws Exception {
        String accessToken = guestAccessToken();
        String eTag = mockMvc.perform(get("/api/v1/regions/GU/boundary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/v1/regions/GU/boundary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.IF_NONE_MATCH, eTag))
                .andExpect(status().isNotModified());
    }

    @Test
    void 지역_목록에_없는_코드는_404다() throws Exception {
        mockMvc.perform(get("/api/v1/regions/XK/boundary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_NOT_FOUND"));
    }

    @Test
    void 경계_데이터가_없는_지역은_따로_알린다() throws Exception {
        mockMvc.perform(get("/api/v1/regions/UM/boundary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_BOUNDARY_NOT_FOUND"));
    }

    @Test
    void 로그인하지_않으면_경계를_주지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/regions/HK/boundary"))
                .andExpect(status().isUnauthorized());
    }

    private String guestAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
