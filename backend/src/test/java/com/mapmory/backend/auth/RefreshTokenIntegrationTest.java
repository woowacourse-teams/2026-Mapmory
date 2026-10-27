package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.token.refresh.RefreshTokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RefreshTokenIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MeterRegistry meterRegistry;

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
    void 소셜_회원은_회전_응답을_받지_못해도_이전_refresh_재사용은_탈취로_보고_모두_폐기한다() throws Exception {
        String lost = login(300_004L);
        String unreceived = refresh(lost);

        mockMvc.perform(refreshRequest(lost))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 소셜 회원은 다시 로그인하면 되므로, 후속 토큰도 폐기한다.
        mockMvc.perform(refreshRequest(unreceived))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 게스트가_회전_응답을_받지_못하면_이전_refresh로_재발급받는다() throws Exception {
        String lost = loginAsGuest();
        String unreceived = refresh(lost);

        String reissued = refresh(lost);

        // 받지 못한 후속 토큰은 폐기되고, 재발급받은 토큰으로 세션이 이어진다.
        mockMvc.perform(refreshRequest(reissued))
                .andExpect(status().isOk());
        assertThat(refreshTokenRepository.findAll())
                .filteredOn(token -> !token.isRevoked())
                .hasSize(1);
        mockMvc.perform(refreshRequest(unreceived))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 게스트가_회전_응답을_연달아_받지_못해도_이전_refresh로_재발급받는다() throws Exception {
        String lost = loginAsGuest();
        refresh(lost);
        refresh(lost);

        String reissued = refresh(lost);

        mockMvc.perform(refreshRequest(reissued))
                .andExpect(status().isOk());
    }

    @Test
    void 게스트라도_후속_토큰이_이미_쓰였다면_이전_refresh_재사용은_탈취로_보고_모두_폐기한다() throws Exception {
        String stolen = loginAsGuest();
        String next = refresh(stolen);
        String latest = refresh(next);

        mockMvc.perform(refreshRequest(stolen))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(refreshRequest(latest))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 게스트라도_로그아웃한_refresh의_이전_토큰으로는_재발급받지_못한다() throws Exception {
        String previous = loginAsGuest();
        String current = refresh(previous);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(current)))
                .andExpect(status().isNoContent());

        mockMvc.perform(refreshRequest(previous))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 재사용으로_전체_폐기하면_재사용_카운터가_오른다() throws Exception {
        double before = reuseCount("REVOKED_ALL", "KAKAO");
        String lost = login(300_005L);
        refresh(lost);

        mockMvc.perform(refreshRequest(lost))
                .andExpect(status().isUnauthorized());

        assertThat(reuseCount("REVOKED_ALL", "KAKAO")).isEqualTo(before + 1);
    }

    @Test
    void 게스트_응답_유실로_재발급하면_재발급_카운터가_오른다() throws Exception {
        double before = reuseCount("REISSUED", "GUEST");
        String lost = loginAsGuest();
        refresh(lost);

        refresh(lost);

        assertThat(reuseCount("REISSUED", "GUEST")).isEqualTo(before + 1);
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
    void 이미_회전된_refresh로_로그아웃하면_후속_토큰까지_폐기한다() throws Exception {
        String previous = login(300_006L);
        String current = refresh(previous);

        logout(previous);

        mockMvc.perform(refreshRequest(current))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 로그아웃과_재발급이_겹쳐도_로그아웃_뒤_쓸_수_있는_refresh가_남지_않는다() throws Exception {
        // 로그아웃이 회전 커밋 전에 읽은 값으로 덮어쓰면, 회전으로 나간 후속 토큰이 살아남는다. (#462)
        // 겹치는 순간이 매번 생기지는 않으므로 여러 번 시도한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (long kakaoId = 300_101L; kakaoId < 300_121L; kakaoId++) {
                String refreshToken = login(kakaoId);
                CountDownLatch start = new CountDownLatch(1);
                Future<MockHttpServletResponse> refreshed = executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(refreshRequest(refreshToken)).andReturn().getResponse();
                });
                Future<MockHttpServletResponse> loggedOut = executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(logoutRequest(refreshToken)).andReturn().getResponse();
                });
                start.countDown();

                assertThat(loggedOut.get().getStatus()).isEqualTo(204);
                MockHttpServletResponse refreshResponse = refreshed.get();
                if (refreshResponse.getStatus() == 200) {
                    String successor = JsonPath.read(refreshResponse.getContentAsString(), "$.data.refreshToken");
                    mockMvc.perform(refreshRequest(successor))
                            .andExpect(status().isUnauthorized());
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 유효하지_않은_refresh는_401_ProblemDetails로_응답한다() throws Exception {
        mockMvc.perform(refreshRequest("not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private String login(long kakaoId) throws Exception {
        willReturn(new SocialIdentity(String.valueOf(kakaoId), "소현"))
                .given(kakaoIdentityAdapter).verify(anyString());

        String responseBody = mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"kakao-id-token\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.data.refreshToken");
    }

    private double reuseCount(String outcome, String provider) {
        Counter counter = meterRegistry.find("mapmory.auth.refresh.reuse")
                .tag("outcome", outcome)
                .tag("provider", provider)
                .counter();
        return counter == null ? 0 : counter.count();
    }

    private String loginAsGuest() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(responseBody, "$.data.refreshToken");
    }

    private String refresh(String refreshToken) throws Exception {
        String responseBody = mockMvc.perform(refreshRequest(refreshToken))
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

    private void logout(String refreshToken) throws Exception {
        mockMvc.perform(logoutRequest(refreshToken))
                .andExpect(status().isNoContent());
    }

    private MockHttpServletRequestBuilder logoutRequest(String refreshToken) {
        return post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(refreshToken));
    }

    private String body(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }
}
