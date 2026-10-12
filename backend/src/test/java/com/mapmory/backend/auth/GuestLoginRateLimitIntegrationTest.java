package com.mapmory.backend.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.kakao.KakaoIdentityAdapter;
import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 게스트 로그인 남용 방지 인수 테스트.
 *
 * 게스트 로그인은 인증 없이 호출되므로 계정을 무제한으로 찍어낼 수 있다.
 * 같은 출처에서 짧은 시간에 반복 호출하면 거절한다. (ADR 0015)
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "guest-login.rate-limit.capacity=2",
        "guest-login.rate-limit.window=1h"
})
class GuestLoginRateLimitIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private KakaoIdentityAdapter kakaoIdentityAdapter;

    @Test
    void 한도까지는_게스트_로그인이_허용된다() throws Exception {
        guestLogin("203.0.113.1").andExpect(status().isOk());
        guestLogin("203.0.113.1").andExpect(status().isOk());
    }

    @Test
    void 한도를_넘으면_429로_거절한다() throws Exception {
        guestLogin("203.0.113.2");
        guestLogin("203.0.113.2");

        guestLogin("203.0.113.2")
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("GUEST_LOGIN_RATE_LIMITED"));
    }

    @Test
    void 다른_출처의_요청은_한도를_공유하지_않는다() throws Exception {
        guestLogin("203.0.113.3");
        guestLogin("203.0.113.3");
        guestLogin("203.0.113.3").andExpect(status().isTooManyRequests());

        guestLogin("203.0.113.4").andExpect(status().isOk());
    }

    @Test
    void 카카오_로그인은_게스트_한도의_영향을_받지_않는다() throws Exception {
        guestLogin("203.0.113.5");
        guestLogin("203.0.113.5");
        guestLogin("203.0.113.5").andExpect(status().isTooManyRequests());

        // 같은 출처라도 카카오 로그인 경로는 막히지 않는다. (토큰이 가짜라 401)
        willThrow(new BusinessException(AuthErrorCode.INVALID_KAKAO_TOKEN))
                .given(kakaoIdentityAdapter).verify(anyString());
        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .header("X-Forwarded-For", "203.0.113.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"kakao-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions guestLogin(String clientIp) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login/guest")
                .header("X-Forwarded-For", clientIp));
    }
}
