package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class KakaoLoginIntegrationTest extends IntegrationTest {

    private static final String REQUEST_BODY = "{\"idToken\":\"kakao-id-token\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 신규_카카오_사용자는_회원으로_생성되고_토큰을_받는다() throws Exception {
        willReturn(kakaoIdentity(100_001L, "소현")).given(kakaoIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.isNewMember").value(true));

        assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "100001"))
                .isPresent();
    }

    @Test
    void 기존_회원은_재로그인시_동일_회원으로_매핑되고_isNewMember는_false다() throws Exception {
        willReturn(kakaoIdentity(100_002L, "소현")).given(kakaoIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(true));

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewMember").value(false));
    }

    @Test
    void 유효하지_않은_카카오_토큰은_401_ProblemDetails로_응답한다() throws Exception {
        willThrow(new BusinessException(AuthErrorCode.INVALID_KAKAO_TOKEN))
                .given(kakaoIdentityAdapter).verify(anyString());

        mockMvc.perform(post("/api/v1/auth/login/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_KAKAO_TOKEN"));
    }

    @Test
    void 같은_카카오_계정의_첫_로그인이_동시에_와도_모두_로그인되고_회원은_하나다() throws Exception {
        // 버튼 연타처럼 같은 계정의 첫 로그인이 겹치면 둘 다 "회원 없음"을 보고 가입하려 한다. (#461)
        // 겹치는 순간이 매번 생기지는 않으므로 여러 번 시도한다.
        willAnswer(invocation -> kakaoIdentity(Long.valueOf(invocation.<String>getArgument(0)), "소현"))
                .given(kakaoIdentityAdapter).verify(anyString());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (long kakaoId = 100_101L; kakaoId < 100_111L; kakaoId++) {
                List<Integer> statuses = loginConcurrently(executor, kakaoId);

                assertThat(statuses).containsOnly(200);
                assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, String.valueOf(kakaoId)))
                        .isPresent();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Integer> loginConcurrently(ExecutorService executor, long kakaoId) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        String body = "{\"idToken\":\"" + kakaoId + "\"}";
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/v1/auth/login/kakao")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        return statuses;
    }

    private SocialIdentity kakaoIdentity(Long id, String nickname) {
        return new SocialIdentity(String.valueOf(id), nickname);
    }
}
