package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.AppleWithdrawalService;
import com.mapmory.backend.auth.application.model.AppleReauthentication;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.auth.token.refresh.RefreshTokenRepository;
import com.mapmory.backend.auth.token.refresh.RefreshTokenService;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 이 클래스에만 실패를 주입해 실제 탈퇴 트랜잭션의 롤백을 검증한다.
    @MockitoSpyBean
    private RefreshTokenService refreshTokenService;

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

    @Test
    void 완료된_탈퇴를_재요청하면_애플을_다시_호출하지_않고_204다() throws Exception {
        String accessToken = appleLogin("retry-sub");
        reauthenticateAs("retry-sub");
        willDoNothing().given(appleRevocationAdapter).revoke(any(), anyString());
        withdrawApple(accessToken).andExpect(status().isNoContent());
        withdrawApple(accessToken).andExpect(status().isNoContent());
        then(appleRevocationAdapter).should().verify("apple-identity-token");
        then(appleRevocationAdapter).should().revoke(new AppleReauthentication("retry-sub", BUNDLE_ID), "auth-code");
    }

    @Test
    void 없는_회원은_탈퇴_성공으로_처리하지_않는다() throws Exception {
        withdrawApple(jwtProvider.issueAccessToken(Long.MAX_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INACTIVE_MEMBER"));
        then(appleRevocationAdapter).should(never()).verify(anyString());
    }

    @Test
    void 버려진_게스트는_탈퇴_성공으로_처리하지_않는다() throws Exception {
        String accessToken = guestAccessToken();
        Member guest = memberRepository.findById(jwtProvider.parseMemberId(accessToken)).orElseThrow();
        guest.abandon(java.time.LocalDateTime.now());
        memberRepository.save(guest);
        withdrawApple(accessToken).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INACTIVE_MEMBER"));
        then(appleRevocationAdapter).should(never()).verify(anyString());
    }

    @Test
    void 탈퇴한_게스트도_애플_탈퇴_성공으로_처리하지_않는다() throws Exception {
        String accessToken = guestAccessToken();
        mockMvc.perform(delete("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        withdrawApple(accessToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_APPLE_MEMBER"));
    }

    @Test
    void 철회_후_DB_실패는_롤백하고_새_재인증으로_복구한다() throws Exception {
        String accessToken = appleLogin("rollback-sub");
        Member before = activeAppleMember("rollback-sub");
        Long memberId = before.getId();
        reauthenticateAs("rollback-sub");
        willDoNothing().given(appleRevocationAdapter).revoke(any(), anyString());
        willAnswer(invocation -> {
            invocation.callRealMethod();
            // 실제 SQL로 refresh 폐기가 수행된 뒤 실패해야 롤백 검증이 의미가 있다.
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM refresh_token WHERE member_id = ? AND revoked_at IS NOT NULL",
                    Long.class, memberId)).isPositive();
            throw new DataAccessResourceFailureException("simulated database failure");
        }).given(refreshTokenService).revokeAll(memberId);

        Logger logger = (Logger) LoggerFactory.getLogger(AppleWithdrawalService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            mockMvc.perform(post("/api/v1/auth/apple/withdrawal")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .header("X-Request-Id", "2d61f3d8-8643-4d2a-a992-6350bb004554")
                            .contentType(MediaType.APPLICATION_JSON).content(withdrawalBody()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("APPLE_WITHDRAWAL_INCOMPLETE"));
            assertThat(logs.list).singleElement().satisfies(event -> {
                assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("event");
                    assertThat(pair.value).isEqualTo("APPLE_WITHDRAWAL_LOCAL_FAILED");
                }).anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("memberId");
                    assertThat(pair.value).isEqualTo(memberId);
                }).anySatisfy(pair -> {
                    assertThat(pair.key).isEqualTo("requestId");
                    assertThat(pair.value).isEqualTo("2d61f3d8-8643-4d2a-a992-6350bb004554");
                });
                assertThat(event.getFormattedMessage()).doesNotContain("auth-code", "apple-identity-token");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
            willCallRealMethod().given(refreshTokenService).revokeAll(memberId);
        }
        Member afterFailure = memberRepository.findById(memberId).orElseThrow();
        assertThat(afterFailure.isActive()).isTrue();
        assertThat(afterFailure.getProviderId()).isEqualTo(before.getProviderId());
        assertThat(afterFailure.getName()).isEqualTo(before.getName());
        assertThat(afterFailure.getNickname()).isEqualTo(before.getNickname());
        assertThat(refreshTokenRepository.findAll()).isNotEmpty().allMatch(token -> !token.isRevoked());

        // 소비한 코드는 재사용하지 않고 재인증으로 받은 새 값을 보낸다.
        willReturn(new AppleReauthentication("rollback-sub", BUNDLE_ID))
                .given(appleRevocationAdapter).verify("fresh-identity-token");
        mockMvc.perform(post("/api/v1/auth/apple/withdrawal")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityToken\":\"fresh-identity-token\",\"authorizationCode\":\"fresh-code\"}"))
                .andExpect(status().isNoContent());
        then(appleRevocationAdapter).should().revoke(new AppleReauthentication("rollback-sub", BUNDLE_ID), "fresh-code");
        assertThat(memberRepository.findById(memberId).orElseThrow().isWithdrawn()).isTrue();
        assertThat(refreshTokenRepository.findAll()).isNotEmpty().allMatch(token -> token.isRevoked());
    }

    private String guestAccessToken() throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/auth/login/guest"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.accessToken");
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
