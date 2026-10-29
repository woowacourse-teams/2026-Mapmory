package com.mapmory.backend.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PushTokenIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private Member jisu;
    private Member minho;

    @BeforeEach
    void setUp() {
        jisu = socialMember("지수");
        minho = socialMember("민호");
    }

    @Test
    void 기기_토큰을_등록한다() throws Exception {
        register(jisu, "phone-1", "ANDROID").andExpect(status().isNoContent());

        PushToken saved = pushTokenRepository.findByToken("phone-1").orElseThrow();
        assertThat(saved.getMemberId()).isEqualTo(jisu.getId());
        assertThat(saved.getPlatform()).isEqualTo(PushPlatform.ANDROID);
    }

    @Test
    void 같은_토큰을_다른_회원이_등록하면_그_회원으로_옮긴다() throws Exception {
        register(jisu, "shared-device", "ANDROID").andExpect(status().isNoContent());

        register(minho, "shared-device", "IOS").andExpect(status().isNoContent());

        assertThat(pushTokenRepository.count()).isEqualTo(1);
        PushToken moved = pushTokenRepository.findByToken("shared-device").orElseThrow();
        assertThat(moved.getMemberId()).isEqualTo(minho.getId());
        assertThat(moved.getPlatform()).isEqualTo(PushPlatform.IOS);
    }

    @Test
    void 플랫폼이나_토큰이_올바르지_않으면_400이다() throws Exception {
        register(jisu, "phone-1", "WINDOWS").andExpect(status().isBadRequest());
        register(jisu, " ", "ANDROID")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 탈퇴한_회원의_남은_토큰으로는_등록하지_못한다() throws Exception {
        jisu.withdraw(LocalDateTime.now());
        memberRepository.save(jisu);

        register(jisu, "phone-1", "ANDROID")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INACTIVE_MEMBER"));
    }

    @Test
    void 내_토큰만_지우고_없는_토큰이어도_204다() throws Exception {
        register(jisu, "jisu-phone", "ANDROID").andExpect(status().isNoContent());

        unregister(minho, "jisu-phone").andExpect(status().isNoContent());
        assertThat(pushTokenRepository.findByToken("jisu-phone")).isPresent();

        unregister(jisu, "jisu-phone").andExpect(status().isNoContent());
        assertThat(pushTokenRepository.findByToken("jisu-phone")).isEmpty();

        unregister(jisu, "never-registered").andExpect(status().isNoContent());
    }

    @Test
    void 탈퇴하면_그_회원의_토큰을_모두_지운다() throws Exception {
        register(jisu, "jisu-phone", "ANDROID").andExpect(status().isNoContent());
        register(jisu, "jisu-tablet", "IOS").andExpect(status().isNoContent());
        register(minho, "minho-phone", "ANDROID").andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(jisu)))
                .andExpect(status().isNoContent());

        assertThat(pushTokenRepository.findAll())
                .extracting(PushToken::getToken)
                .containsExactly("minho-phone");
    }

    @Test
    void 게스트가_버려지면_그_게스트의_토큰을_지운다() throws Exception {
        willReturn(new SocialIdentity("700100", "지수")).given(kakaoIdentityAdapter).verify(anyString());
        kakaoLogin(null);
        String guestAccessToken = JsonPath.read(
                mockMvc.perform(post("/api/v1/auth/login/guest")).andReturn().getResponse().getContentAsString(),
                "$.data.accessToken");
        register("Bearer " + guestAccessToken, "guest-phone", "ANDROID").andExpect(status().isNoContent());
        register(minho, "minho-phone", "ANDROID").andExpect(status().isNoContent());

        kakaoLogin(guestAccessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guestAbandoned").value(true));

        assertThat(pushTokenRepository.findAll())
                .extracting(PushToken::getToken)
                .containsExactly("minho-phone");
    }

    private ResultActions kakaoLogin(String guestAccessToken) throws Exception {
        var request = post("/api/v1/auth/login/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"kakao-id-token\"}");
        if (guestAccessToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + guestAccessToken);
        }
        return mockMvc.perform(request);
    }

    private ResultActions register(Member member, String token, String platform) throws Exception {
        return register(bearer(member), token, platform);
    }

    private ResultActions register(String authorization, String token, String platform) throws Exception {
        return mockMvc.perform(put("/api/v1/push-tokens")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\", \"platform\": \"" + platform + "\"}"));
    }

    private ResultActions unregister(Member member, String token) throws Exception {
        return mockMvc.perform(delete("/api/v1/push-tokens")
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"));
    }

    private Member socialMember(String nickname) {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), nickname, UUID.randomUUID());
        member.changeNickname(nickname);
        return memberRepository.save(member);
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.issueAccessToken(member.getId());
    }
}
