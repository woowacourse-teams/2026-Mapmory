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
import com.mapmory.backend.auth.infrastructure.google.GoogleIdentityAdapter;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class GoogleLoginIntegrationTest extends IntegrationTest {

    private static final String REQUEST_BODY = "{\"idToken\":\"google-id-token\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoSpyBean
    private GoogleIdentityAdapter googleIdentityAdapter;

    @Test
    void 신규_구글_사용자는_회원으로_생성되고_토큰을_받는다() throws Exception {
        willReturn(new SocialIdentity("google-sub-1", "소현")).given(googleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewMember").value(true));

        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-1"))
                .isPresent();
    }

    @Test
    void 기존_회원은_재로그인시_동일_회원으로_매핑되고_isNewMember는_false다() throws Exception {
        willReturn(new SocialIdentity("google-sub-2", "소현")).given(googleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(jsonPath("$.data.isNewMember").value(true));

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(false));
    }

    @Test
    void 게스트가_구글_로그인하면_같은_회원이_구글_회원으로_승격된다() throws Exception {
        String guestAccessToken = JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/login/guest"))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");
        long memberCountBeforePromotion = memberRepository.count();
        willReturn(new SocialIdentity("google-sub-3", "소현")).given(googleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(false));

        assertThat(memberRepository.count()).isEqualTo(memberCountBeforePromotion);
        Member promoted = memberRepository
                .findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-3")
                .orElseThrow();
        assertThat(promoted.getName()).isEqualTo("소현");
    }

    @Test
    void 같은_sub라도_카카오_회원과는_구분된다() throws Exception {
        willReturn(new SocialIdentity("100003", "소현")).given(googleIdentityAdapter).verify(anyString());
        memberRepository.save(Member.ofOAuth(AuthProvider.KAKAO, "100003", "카카오회원", UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(true));
    }

    @Test
    void 이름이_50자를_넘으면_50자로_잘라_저장한다() throws Exception {
        willReturn(new SocialIdentity("google-sub-4", "가".repeat(60)))
                .given(googleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk());

        Member member = memberRepository
                .findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-4")
                .orElseThrow();
        assertThat(member.getName()).isEqualTo("가".repeat(50));
    }

    @Test
    void 이름이_없으면_기본_이름을_부여한다() throws Exception {
        willReturn(new SocialIdentity("google-sub-5", null)).given(googleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk());

        Member member = memberRepository
                .findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-5")
                .orElseThrow();
        assertThat(member.getName()).matches("회원\\d{5}");
    }

    @Test
    void 유효하지_않은_구글_토큰은_401_ProblemDetails로_응답한다() throws Exception {
        willThrow(new BusinessException(AuthErrorCode.INVALID_GOOGLE_TOKEN))
                .given(googleIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"));
    }

    @Test
    void idToken이_비어_있으면_400으로_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
