package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class MemberIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void 게스트는_기본_이름과_GUEST로_조회된다() throws Exception {
        String accessToken = guestAccessToken();

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(matchesPattern("회원[1-9]\\d{4}")))
                .andExpect(jsonPath("$.data.provider").value("GUEST"))
                .andExpect(jsonPath("$.data.memberId").doesNotExist())
                .andExpect(jsonPath("$.data.id").doesNotExist());
    }

    @Test
    void 소셜_회원은_제공자_이름이_아닌_기본_이름_닉네임과_제공자로_조회된다() throws Exception {
        Member member = memberRepository.save(Member.ofOAuth(AuthProvider.KAKAO, "400001", "소현", UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(member.getNickname()))
                .andExpect(jsonPath("$.data.nickname").value(matchesPattern("회원[1-9]\\d{4}")))
                .andExpect(jsonPath("$.data.provider").value("KAKAO"));
    }

    @Test
    void 닉네임을_바꾸면_앞뒤_공백을_지워_저장하고_바뀐_정보를_응답한다() throws Exception {
        String accessToken = guestAccessToken();

        changeNickname("Bearer " + accessToken, "  지수  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("지수"))
                .andExpect(jsonPath("$.data.provider").value("GUEST"));

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(jsonPath("$.data.nickname").value("지수"));
    }

    @Test
    void 닉네임을_바꿔도_제공자가_준_이름은_그대로다() throws Exception {
        Member member = memberRepository.save(Member.ofOAuth(AuthProvider.GOOGLE, "google-401", "김소현", UUID.randomUUID()));

        changeNickname(bearer(member), "지수")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").doesNotExist());

        Member changed = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(changed.getNickname()).isEqualTo("지수");
        assertThat(changed.getName()).isEqualTo("김소현");
    }

    @Test
    void 다른_회원과_같은_닉네임도_쓸_수_있다() throws Exception {
        Member other = Member.ofOAuth(AuthProvider.GOOGLE, "google-400", "김지수", UUID.randomUUID());
        other.changeNickname("지수");
        memberRepository.save(other);
        Member member = memberRepository.save(Member.ofOAuth(AuthProvider.KAKAO, "400002", "소현", UUID.randomUUID()));

        changeNickname(bearer(member), "지수")
                .andExpect(status().isOk());

        assertThat(memberRepository.findById(other.getId()).orElseThrow().getNickname()).isEqualTo("지수");
        assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname()).isEqualTo("지수");
    }

    // 21자 값은 20자 제한 초과. 마지막 값은 JSON 이스케이프(\n)라 서버에는 줄바꿈(제어 문자)으로 들어간다.
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "가나다라마바사아자차카타파하가나다라마바사", "지\\n수"})
    void 규칙에_맞지_않는_닉네임은_400이고_닉네임이_바뀌지_않는다(String nickname) throws Exception {
        Member member = memberRepository.save(Member.ofOAuth(AuthProvider.KAKAO, "400003", "소현", UUID.randomUUID()));

        changeNickname(bearer(member), nickname)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname()).isEqualTo(member.getNickname());
    }

    @Test
    void 닉네임이_없으면_400이다() throws Exception {
        Member member = memberRepository.save(Member.ofOAuth(AuthProvider.KAKAO, "400004", "소현", UUID.randomUUID()));

        mockMvc.perform(patch("/api/v1/members/me/nickname")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        changeNickname(null, "지수")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 회원_행이_없는_토큰이면_404다() throws Exception {
        String accessToken = jwtProvider.issueAccessToken(Long.MAX_VALUE);

        mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        changeNickname("Bearer " + accessToken, "지수")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    private String guestAccessToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.issueAccessToken(member.getId());
    }

    private ResultActions changeNickname(String authorization, String nickname) throws Exception {
        var request = patch("/api/v1/members/me/nickname")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }
}
