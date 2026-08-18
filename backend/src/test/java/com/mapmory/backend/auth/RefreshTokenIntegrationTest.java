package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.kakao.KakaoApiClient;
import com.mapmory.backend.auth.kakao.KakaoUserResponse;
import com.mapmory.backend.auth.kakao.KakaoUserResponse.KakaoAccount;
import com.mapmory.backend.auth.kakao.KakaoUserResponse.KakaoAccount.Profile;
import com.mapmory.backend.auth.refresh.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class RefreshTokenIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private KakaoApiClient kakaoApiClient;

    @Test
    void 로그인_시_refresh는_원문이_아닌_해시로_저장된다() throws Exception {
        String refreshToken = login(300_001L);

        // 원문 그대로는 조회되지 않는다(해시로 저장됨).
        assertThat(refreshTokenRepository.findByTokenHash(refreshToken)).isEmpty();
    }

    @Test
    void refresh로_새_토큰을_발급받고_이전_refresh는_무효화된다() throws Exception {
        String refreshToken = login(300_002L);

        mockMvc.perform(refreshRequest(refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

        // 회전된(폐기된) 이전 refresh 재사용 → 401
        mockMvc.perform(refreshRequest(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 로그아웃하면_refresh가_폐기되어_재발급이_401이다() throws Exception {
        String refreshToken = login(300_003L);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(refreshRequest(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 유효하지_않은_refresh는_401_ProblemDetails로_응답한다() throws Exception {
        mockMvc.perform(refreshRequest("not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private String login(long kakaoId) throws Exception {
        given(kakaoApiClient.fetchUser(anyString()))
                .willReturn(new KakaoUserResponse(kakaoId, new KakaoAccount(new Profile("소현"))));

        String responseBody = mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"kakao-token\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.data.refreshToken");
    }

    private MockHttpServletRequestBuilder refreshRequest(String refreshToken) {
        return post("/api/v1/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(refreshToken));
    }

    private String body(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }
}
