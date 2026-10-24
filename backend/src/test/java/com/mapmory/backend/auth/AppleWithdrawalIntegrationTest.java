package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.model.AppleReauthentication;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 애플 회원 탈퇴 인수 테스트. 애플 토큰을 철회한 뒤에만 탈퇴한다. (ADR 0025)
 */
class AppleWithdrawalIntegrationTest extends IntegrationTest {

    private static final String BUNDLE_ID = "com.mapmory.ios";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 재인증한_본인_계정이면_애플_토큰을_철회하고_탈퇴한다() throws Exception {
        String accessToken = appleLogin("apple-sub-w1");
        reauthenticateAs("apple-sub-w1");
        willDoNothing().given(appleRevocationAdapter).revoke(any(), anyString());

        withdrawApple(accessToken).andExpect(status().isNoContent());

        then(appleRevocationAdapter).should().revoke(new AppleReauthentication("apple-sub-w1", BUNDLE_ID), "auth-code");
        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-w1")).isEmpty();
    }

    @Test
    void 다른_애플_계정으로_재인증하면_철회하지_않고_탈퇴하지_않는다() throws Exception {
        String accessToken = appleLogin("apple-sub-w2");
        reauthenticateAs("someone-else");

        withdrawApple(accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APPLE_REAUTHENTICATION_FAILED"));

        then(appleRevocationAdapter).should(never()).revoke(any(), anyString());
        assertThat(activeAppleMember("apple-sub-w2").isActive()).isTrue();
    }

    @Test
    void 철회가_실패하면_탈퇴하지_않는다() throws Exception {
        String accessToken = appleLogin("apple-sub-w3");
        reauthenticateAs("apple-sub-w3");
        willThrow(new BusinessException(AuthErrorCode.APPLE_UNAVAILABLE))
                .given(appleRevocationAdapter).revoke(any(), anyString());

        withdrawApple(accessToken)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("APPLE_UNAVAILABLE"));

        assertThat(activeAppleMember("apple-sub-w3").isActive()).isTrue();
    }

    @Test
    void 재인증이_잘못됐으면_400이고_탈퇴하지_않는다() throws Exception {
        String accessToken = appleLogin("apple-sub-w4");
        willThrow(new BusinessException(AuthErrorCode.APPLE_REAUTHENTICATION_FAILED))
                .given(appleRevocationAdapter).verify(anyString());

        withdrawApple(accessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APPLE_REAUTHENTICATION_FAILED"));

        assertThat(activeAppleMember("apple-sub-w4").isActive()).isTrue();
    }

    @Test
    void 애플_회원이_아니면_400_NOT_APPLE_MEMBER다() throws Exception {
        String guestAccessToken = JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/login/guest")).andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");

        withdrawApple(guestAccessToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_APPLE_MEMBER"));
    }

    @Test
    void 애플_회원은_일반_탈퇴로_탈퇴할_수_없다() throws Exception {
        String accessToken = appleLogin("apple-sub-w5");

        mockMvc.perform(delete("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APPLE_REAUTHENTICATION_REQUIRED"));

        assertThat(activeAppleMember("apple-sub-w5").isActive()).isTrue();
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/apple/withdrawal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withdrawalBody()))
                .andExpect(status().isUnauthorized());
    }

    private String appleLogin(String sub) throws Exception {
        willReturn(new SocialIdentity(sub, null)).given(appleIdentityAdapter).verify(anyString());
        String body = mockMvc.perform(post("/api/v1/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityToken\":\"apple-identity-token\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private void reauthenticateAs(String sub) {
        willReturn(new AppleReauthentication(sub, BUNDLE_ID)).given(appleRevocationAdapter).verify(anyString());
    }

    private ResultActions withdrawApple(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/apple/withdrawal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withdrawalBody()));
    }

    private static String withdrawalBody() {
        return "{\"identityToken\":\"apple-identity-token\",\"authorizationCode\":\"auth-code\"}";
    }

    private Member activeAppleMember(String sub) {
        return memberRepository.findByProviderAndProviderId(AuthProvider.APPLE, sub).orElseThrow();
    }
}
