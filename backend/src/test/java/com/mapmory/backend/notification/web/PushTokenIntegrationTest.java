package com.mapmory.backend.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.application.PushTokenService;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PushTokenIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PushTokenService pushTokenService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Member jisu;
    private Member minho;

    @BeforeEach
    void setUp() {
        jisu = socialMember("지수");
        minho = socialMember("민호");
    }

    @Test
    void 기기_토큰을_등록한다() throws Exception {
        register(jisu, "phone-1", "ANDROID")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushTokenId").isNumber());

        PushToken saved = pushTokenRepository.findByToken("phone-1").orElseThrow();
        assertThat(saved.getMemberId()).isEqualTo(jisu.getId());
        assertThat(saved.getPlatform()).isEqualTo(PushPlatform.ANDROID);
        register(jisu, "phone-1", "IOS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pushTokenId").value(saved.getId()));
        assertThat(pushTokenRepository.findById(saved.getId()).orElseThrow().getPlatform())
                .isEqualTo(PushPlatform.IOS);
    }

    @Test
    void 같은_토큰을_다른_회원이_등록하면_새_등록_ID를_발급한다() throws Exception {
        register(jisu, "shared-device", "ANDROID").andExpect(status().isOk());
        Long previousId = pushTokenRepository.findByToken("shared-device").orElseThrow().getId();

        register(minho, "shared-device", "IOS").andExpect(status().isOk());

        assertThat(pushTokenRepository.count()).isEqualTo(1);
        PushToken moved = pushTokenRepository.findByToken("shared-device").orElseThrow();
        assertThat(moved.getMemberId()).isEqualTo(minho.getId());
        assertThat(moved.getPlatform()).isEqualTo(PushPlatform.IOS);
        assertThat(moved.getId()).isNotEqualTo(previousId);
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
    void 내_등록만_지우고_없는_ID여도_204다() throws Exception {
        register(jisu, "jisu-phone", "ANDROID").andExpect(status().isOk());
        Long registrationId = pushTokenRepository.findByToken("jisu-phone").orElseThrow().getId();

        unregister(minho, registrationId).andExpect(status().isNoContent());
        assertThat(pushTokenRepository.findByToken("jisu-phone")).isPresent();

        unregister(jisu, registrationId).andExpect(status().isNoContent());
        assertThat(pushTokenRepository.findByToken("jisu-phone")).isEmpty();

        unregister(jisu, registrationId).andExpect(status().isNoContent());
    }

    @Test
    void 이전_등록의_중복_삭제가_재등록한_행을_지우지_않는다() throws Exception {
        register(jisu, "same-token", "ANDROID").andExpect(status().isOk());
        Long oldId = pushTokenRepository.findByToken("same-token").orElseThrow().getId();
        unregister(jisu, oldId).andExpect(status().isNoContent());

        register(jisu, "same-token", "ANDROID").andExpect(status().isOk());
        Long newId = pushTokenRepository.findByToken("same-token").orElseThrow().getId();
        assertThat(newId).isNotEqualTo(oldId);

        unregister(jisu, oldId).andExpect(status().isNoContent());
        assertThat(pushTokenRepository.findById(newId)).isPresent();
    }

    @Test
    void 새_등록_생성에_실패하면_이전_등록_삭제도_롤백한다() {
        PushToken previous = pushTokenService.register(jisu.getId(), "rollback-token", PushPlatform.ANDROID);

        // 다른 회원으로 전환하면서 DELETE·flush 후 새 엔티티 생성에 실패시킨다.
        assertThatNullPointerException().isThrownBy(() ->
                pushTokenService.register(minho.getId(), "rollback-token", null));

        PushToken restored = pushTokenRepository.findByToken("rollback-token").orElseThrow();
        assertThat(restored.getId()).isEqualTo(previous.getId());
        assertThat(restored.getMemberId()).isEqualTo(jisu.getId());
    }

    @Test
    void 삭제_등록_ID는_필수이고_양수여야_한다() throws Exception {
        unregister(jisu, null).andExpect(status().isBadRequest());
        unregister(jisu, 0L).andExpect(status().isBadRequest());
        unregister(jisu, -1L).andExpect(status().isBadRequest());
    }

    @Test
    void 동시에_최초_등록해도_한_행만_저장되고_성공_또는_충돌을_응답한다() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<ResultActions> request = () -> {
            start.await(10, TimeUnit.SECONDS);
            return register(jisu, "concurrent-token", "ANDROID");
        };

        List<ResultActions> responses;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(request);
            var second = executor.submit(request);
            responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }

        PushToken saved = pushTokenRepository.findByToken("concurrent-token").orElseThrow();
        assertThat(pushTokenRepository.count()).isEqualTo(1);
        assertThat(responses).anySatisfy(response -> response.andExpect(status().isOk()));

        for (ResultActions response : responses) {
            int status = response.andReturn().getResponse().getStatus();
            assertThat(status).isIn(200, 409);
            if (status == 200) {
                response.andExpect(jsonPath("$.data.pushTokenId").value(saved.getId()));
            } else {
                response.andExpect(jsonPath("$.code").value("PUSH_REGISTRATION_CONFLICT"));
            }
        }
    }

    @Test
    void 외부_트랜잭션이_롤백돼도_독립적으로_완료한_등록은_유지한다() {
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        outerTransaction.executeWithoutResult(status -> {
            pushTokenService.register(jisu.getId(), "independent-token", PushPlatform.ANDROID);
            status.setRollbackOnly();
        });

        assertThat(pushTokenRepository.findByToken("independent-token"))
                .hasValueSatisfying(saved -> assertThat(saved.getMemberId()).isEqualTo(jisu.getId()));
    }

    private ResultActions register(Member member, String token, String platform) throws Exception {
        return mockMvc.perform(put("/api/v1/push-tokens")
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\", \"platform\": \"" + platform + "\"}"));
    }

    private ResultActions unregister(Member member, Long pushTokenId) throws Exception {
        return mockMvc.perform(delete("/api/v1/push-tokens")
                .header(HttpHeaders.AUTHORIZATION, bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pushTokenId\": " + pushTokenId + "}"));
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
