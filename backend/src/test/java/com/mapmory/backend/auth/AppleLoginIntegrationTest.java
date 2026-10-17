package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
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

class AppleLoginIntegrationTest extends IntegrationTest {

    private static final String REQUEST_BODY = "{\"identityToken\":\"apple-identity-token\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 신규_애플_사용자는_기본_이름으로_회원이_생성되고_토큰을_받는다() throws Exception {
        willReturn(new SocialIdentity("apple-sub-1", null)).given(appleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewMember").value(true));

        Member member = memberRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-1")
                .orElseThrow();
        assertThat(member.getName()).matches("회원\\d{5}");
    }

    @Test
    void 기존_회원은_재로그인시_동일_회원으로_매핑되고_isNewMember는_false다() throws Exception {
        willReturn(new SocialIdentity("apple-sub-3", null)).given(appleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(jsonPath("$.data.isNewMember").value(true));

        mockMvc.perform(post("/api/v1/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(false));
    }

    @Test
    void 게스트가_애플_로그인하면_같은_회원이_애플_회원으로_승격된다() throws Exception {
        String guestAccessToken = JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/login/guest"))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");
        long memberCountBeforePromotion = memberRepository.count();
        willReturn(new SocialIdentity("apple-sub-4", null)).given(appleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/apple")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(false));

        assertThat(memberRepository.count()).isEqualTo(memberCountBeforePromotion);
        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-4"))
                .isPresent();
    }

    @Test
    void 유효하지_않은_애플_토큰은_401_ProblemDetails로_응답한다() throws Exception {
        willThrow(new BusinessException(AuthErrorCode.INVALID_APPLE_TOKEN))
                .given(appleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_APPLE_TOKEN"));
    }

    @Test
    void identityToken이_비어_있으면_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
