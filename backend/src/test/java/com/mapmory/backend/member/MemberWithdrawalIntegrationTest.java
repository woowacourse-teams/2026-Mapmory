package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.member.event.MemberWithdrawn;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 회원 탈퇴 인수 테스트. (ADR 0021)
 */
@RecordApplicationEvents
class MemberWithdrawalIntegrationTest extends IntegrationTest {

    private static final String KAKAO_LOGIN_BODY = "{\"kakaoAccessToken\":\"kakao-token\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private ApplicationEvents events;

    @Test
    void 소셜_회원이_탈퇴하면_204이고_개인정보를_지운다() throws Exception {
        String loginBody = kakaoLogin(null, "700001");
        String accessToken = JsonPath.read(loginBody, "$.data.accessToken");
        Member before = memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "700001").orElseThrow();

        withdraw(accessToken).andExpect(status().isNoContent());

        Member withdrawn = memberRepository.findById(before.getId()).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.getWithdrawnAt()).isNotNull();
        assertThat(withdrawn.getName()).isEqualTo("탈퇴한 회원");
        assertThat(withdrawn.getNickname()).isEqualTo("탈퇴한 회원");
        assertThat(withdrawn.getProviderId()).isNull();
        assertThat(withdrawn.getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(withdrawn.getUuid()).isEqualTo(before.getUuid());
    }

    @Test
    void 탈퇴하면_MemberWithdrawn을_한_번_발행한다() throws Exception {
        String accessToken = JsonPath.read(kakaoLogin(null, "700002"), "$.data.accessToken");
        Member member = memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "700002").orElseThrow();

        withdraw(accessToken).andExpect(status().isNoContent());

        assertThat(events.stream(MemberWithdrawn.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.memberId()).isEqualTo(member.getId());
                    assertThat(event.provider()).isEqualTo(AuthProvider.KAKAO);
                    assertThat(event.withdrawnAt()).isNotNull();
                });
    }

    @Test
    void 두_번_탈퇴해도_204이고_이벤트는_한_번만_발행한다() throws Exception {
        String accessToken = JsonPath.read(kakaoLogin(null, "700003"), "$.data.accessToken");

        withdraw(accessToken).andExpect(status().isNoContent());
        withdraw(accessToken).andExpect(status().isNoContent());

        assertThat(events.stream(MemberWithdrawn.class)).hasSize(1);
    }

    @Test
    void 탈퇴하면_모든_refresh_토큰을_폐기해_재발급받을_수_없다() throws Exception {
        String firstLogin = kakaoLogin(null, "700004");
        String secondLogin = kakaoLogin(null, "700004");
        String accessToken = JsonPath.read(secondLogin, "$.data.accessToken");

        withdraw(accessToken).andExpect(status().isNoContent());

        refresh(JsonPath.read(firstLogin, "$.data.refreshToken"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(JsonPath.read(secondLogin, "$.data.refreshToken"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 폐기되지_않은_refresh가_남아_있어도_탈퇴한_회원이면_재발급하지_않는다() throws Exception {
        // 탈퇴와 재발급이 동시에 와서 폐기 전에 검증을 통과한 경우를 흉내 낸다. 이벤트 없이 회원만 탈퇴시킨다.
        String loginBody = kakaoLogin(null, "700007");
        Member member = memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "700007").orElseThrow();
        member.withdraw(LocalDateTime.now());
        memberRepository.save(member);

        refresh(JsonPath.read(loginBody, "$.data.refreshToken"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void 탈퇴한_소셜_계정으로_다시_로그인하면_새_회원이_된다() throws Exception {
        String accessToken = JsonPath.read(kakaoLogin(null, "700005"), "$.data.accessToken");
        Member withdrawn = memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "700005").orElseThrow();
        withdraw(accessToken).andExpect(status().isNoContent());

        String loginBody = kakaoLogin(null, "700005");

        assertThat((Boolean) JsonPath.read(loginBody, "$.data.isNewMember")).isTrue();
        Member rejoined = memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "700005").orElseThrow();
        assertThat(rejoined.getId()).isNotEqualTo(withdrawn.getId());
        assertThat(rejoined.isActive()).isTrue();
    }

    @Test
    void 게스트도_탈퇴할_수_있다() throws Exception {
        String guestLogin = guestLogin();
        String accessToken = JsonPath.read(guestLogin, "$.data.accessToken");

        withdraw(accessToken).andExpect(status().isNoContent());

        refresh(JsonPath.read(guestLogin, "$.data.refreshToken"))
                .andExpect(status().isUnauthorized());
        assertThat(events.stream(MemberWithdrawn.class))
                .singleElement()
                .extracting(MemberWithdrawn::provider)
                .isEqualTo(AuthProvider.GUEST);
    }

    @Test
    void 탈퇴한_게스트의_남은_access_token으로_소셜_로그인하면_승격하지_않고_새로_가입한다() throws Exception {
        String guestAccessToken = JsonPath.read(guestLogin(), "$.data.accessToken");
        Long guestId = jwtProvider.parseMemberId(guestAccessToken);
        withdraw(guestAccessToken).andExpect(status().isNoContent());

        String loginBody = kakaoLogin(guestAccessToken, "700006");

        assertThat((Boolean) JsonPath.read(loginBody, "$.data.isNewMember")).isTrue();
        Member joined = memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "700006").orElseThrow();
        assertThat(joined.getId()).isNotEqualTo(guestId);
        Member withdrawnGuest = memberRepository.findById(guestId).orElseThrow();
        assertThat(withdrawnGuest.isWithdrawn()).isTrue();
        assertThat(withdrawnGuest.getProvider()).isEqualTo(AuthProvider.GUEST);
        assertThat(withdrawnGuest.getName()).isEqualTo("탈퇴한 회원");
    }

    @Test
    void 탈퇴한_회원의_남은_access_token으로_회원_정보를_조회하거나_바꾸면_404다() throws Exception {
        String accessToken = JsonPath.read(guestLogin(), "$.data.accessToken");
        withdraw(accessToken).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/members/me/nickname")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"지수\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void 회원_행이_없는_토큰이면_404다() throws Exception {
        String accessToken = jwtProvider.issueAccessToken(Long.MAX_VALUE);

        withdraw(accessToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        assertThat(events.stream(MemberWithdrawn.class)).isEmpty();
    }

    private ResultActions withdraw(String accessToken) throws Exception {
        return mockMvc.perform(delete("/api/v1/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/token/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private String guestLogin() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String kakaoLogin(String bearerToken, String kakaoId) throws Exception {
        willReturn(new SocialIdentity(kakaoId, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        var request = post("/api/v1/auth/login/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(KAKAO_LOGIN_BODY);
        if (bearerToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
