package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.infrastructure.kakao.KakaoIdentityAdapter;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 게스트 → 카카오 승격 인수 테스트.
 *
 * "게스트로 남긴 기록이 계정을 연결한 뒤에도 그대로 보인다"를 사용자 관점에서 검증한다.
 * 승격은 회원 행의 provider만 교체하므로 기록을 옮기지 않는다. (ADR 0015)
 */
@AutoConfigureMockMvc
class GuestPromotionIntegrationTest extends IntegrationTest {

    private static final String KAKAO_LOGIN_BODY = "{\"kakaoAccessToken\":\"kakao-token\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @MockitoSpyBean
    private KakaoIdentityAdapter kakaoIdentityAdapter;

    @Test
    void 게스트가_카카오_로그인하면_남긴_기록이_그대로_유지된다() throws Exception {
        String guestAccessToken = guestLogin("accessToken");
        createTravelRecord(guestAccessToken, "제주도 여행");
        long memberCountBeforePromotion = memberRepository.count();
        willReturn(kakaoIdentity(200_001L, "소현")).given(kakaoIdentityAdapter).verify(anyString());

        String promotedAccessToken = kakaoLogin(guestAccessToken, "accessToken");

        mockMvc.perform(get("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + promotedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("제주도 여행"));

        assertThat(memberRepository.count()).isEqualTo(memberCountBeforePromotion);
        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "200001"))
                .isPresent();
    }

    @Test
    void 승격된_회원은_카카오_닉네임을_이름으로_갖는다() throws Exception {
        String guestAccessToken = guestLogin("accessToken");
        long memberCountBeforePromotion = memberRepository.count();
        willReturn(kakaoIdentity(200_002L, "소현")).given(kakaoIdentityAdapter).verify(anyString());

        kakaoLogin(guestAccessToken, "accessToken");

        assertThat(memberRepository.count()).isEqualTo(memberCountBeforePromotion);

        Member promoted = memberRepository
                .findByProviderAndProviderId(AuthProvider.KAKAO, "200002")
                .orElseThrow();
        assertThat(promoted.getName()).isEqualTo("소현");
    }

    @Test
    void 이미_가입한_카카오_계정이면_기존_회원으로_로그인되고_게스트_기록은_이어지지_않는다() throws Exception {
        willReturn(kakaoIdentity(200_003L, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        kakaoLogin(null, "accessToken");

        String guestAccessToken = guestLogin("accessToken");
        createTravelRecord(guestAccessToken, "게스트가 남긴 기록");

        String accessToken = kakaoLogin(guestAccessToken, "accessToken");

        mockMvc.perform(get("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void 충돌로_버려진_게스트는_토큰을_재발급받을_수_없다() throws Exception {
        willReturn(kakaoIdentity(200_004L, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        kakaoLogin(null, "accessToken");

        // 같은 게스트의 access/refresh 여야 하므로 한 번만 로그인한다
        String guestLoginBody = guestLogin();
        String guestAccessToken = JsonPath.read(guestLoginBody, "$.data.accessToken");
        String guestRefreshToken = JsonPath.read(guestLoginBody, "$.data.refreshToken");

        kakaoLogin(guestAccessToken, "accessToken");

        mockMvc.perform(post("/api/v1/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + guestRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 게스트_토큰_없이_카카오_로그인하면_새_회원이_생성된다() throws Exception {
        willReturn(kakaoIdentity(200_005L, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        long before = memberRepository.count();

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KAKAO_LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(true));

        assertThat(memberRepository.count()).isEqualTo(before + 1);
    }

    private String guestLogin(String field) throws Exception {
        return JsonPath.read(guestLogin(), "$.data." + field);
    }

    private String guestLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String kakaoLogin(String bearerToken, String field) throws Exception {
        var request = post("/api/v1/auth/login/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(KAKAO_LOGIN_BODY);
        if (bearerToken != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data." + field);
    }

    private void createTravelRecord(String accessToken, String title) throws Exception {
        String body = """
                {
                  "countryCode": "KR",
                  "provinceCode": "49",
                  "districtCode": "50110",
                  "title": "%s",
                  "content": "기록 본문",
                  "startDate": "2026-08-01"
                }
                """.formatted(title);

        mockMvc.perform(post("/api/v1/travel-records")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private SocialIdentity kakaoIdentity(Long id, String nickname) {
        return new SocialIdentity(String.valueOf(id), nickname);
    }
}
