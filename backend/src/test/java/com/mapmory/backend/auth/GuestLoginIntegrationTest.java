package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 게스트 로그인 인수 테스트.
 *
 * "로그인하지 않고 서비스를 쓸 수 있다"를 사용자 관점에서 검증한다.
 * 카카오 로그인과 달리 외부 호출이 없으므로 대역이 필요하지 않다. (ADR 0015)
 */
class GuestLoginIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 게스트_로그인은_회원을_생성하고_토큰을_발급한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewMember").value(true));

        assertThat(memberRepository.findAll())
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getProvider()).isEqualTo(AuthProvider.GUEST);
                    assertThat(member.getProviderId()).isNotBlank();
                });
    }

    @Test
    void 게스트_로그인은_호출할_때마다_새_회원을_만든다() throws Exception {
        long before = memberRepository.count();

        login();
        login();

        assertThat(memberRepository.count()).isEqualTo(before + 2);
    }

    @Test
    void 게스트_토큰으로_보호_API를_호출할_수_있다() throws Exception {
        String accessToken = login("accessToken");

        mockMvc.perform(get("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void 게스트_토큰도_회전으로_재발급된다() throws Exception {
        String refreshToken = login("refreshToken");

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    private String login(String field) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data." + field);
    }

    private void login() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk());
    }
}
