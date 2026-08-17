package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.kakao.KakaoApiClient;
import com.mapmory.backend.auth.kakao.KakaoUserResponse;
import com.mapmory.backend.auth.kakao.KakaoUserResponse.KakaoAccount;
import com.mapmory.backend.auth.kakao.KakaoUserResponse.KakaoAccount.Profile;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class KakaoLoginIntegrationTest extends IntegrationTest {

    private static final String REQUEST_BODY = "{\"kakaoAccessToken\":\"kakao-token\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoBean
    private KakaoApiClient kakaoApiClient;

    @Test
    void 신규_카카오_사용자는_회원으로_생성되고_토큰을_받는다() throws Exception {
        given(kakaoApiClient.fetchUser(anyString()))
                .willReturn(kakaoUser(100_001L, "소현"));

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewMember").value(true));

        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "100001"))
                .isPresent();
    }

    @Test
    void 기존_회원은_재로그인시_동일_회원으로_매핑되고_isNewMember는_false다() throws Exception {
        given(kakaoApiClient.fetchUser(anyString()))
                .willReturn(kakaoUser(100_002L, "소현"));

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewMember").value(true));

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewMember").value(false));
    }

    @Test
    void 유효하지_않은_카카오_토큰은_401_ProblemDetails로_응답한다() throws Exception {
        given(kakaoApiClient.fetchUser(anyString()))
                .willThrow(new BusinessException(AuthErrorCode.INVALID_KAKAO_TOKEN));

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_KAKAO_TOKEN"));
    }

    private KakaoUserResponse kakaoUser(Long id, String nickname) {
        return new KakaoUserResponse(id, new KakaoAccount(new Profile(nickname)));
    }
}
