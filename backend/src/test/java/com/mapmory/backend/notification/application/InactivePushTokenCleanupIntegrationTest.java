package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.application.MemberService;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 탈퇴하거나 버려진 회원의 기기 토큰을 하루 한 번 지운다. (#546)
 */
class InactivePushTokenCleanupIntegrationTest extends IntegrationTest {

    @Autowired
    private InactivePushTokenCleanup cleanup;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Test
    void 탈퇴했거나_버려진_회원의_기기_토큰만_지운다() {
        Member active = social();
        Member withdrawn = social();
        Member abandoned = Member.ofGuest("guest-" + UUID.randomUUID(), "게스트", UUID.randomUUID());
        abandoned.abandon(LocalDateTime.of(2026, 11, 1, 0, 0));
        memberRepository.save(abandoned);
        save(active, "active-phone");
        save(withdrawn, "withdrawn-phone");
        save(withdrawn, "withdrawn-tablet");
        save(abandoned, "abandoned-phone");
        memberService.withdraw(withdrawn.getId());

        int removed = cleanup.run();

        assertThat(removed).isEqualTo(3);
        assertThat(pushTokenRepository.findAll()).extracting(PushToken::getToken).containsExactly("active-phone");
    }

    @Test
    void 지운_뒤_다시_돌려도_활동_중인_회원의_토큰은_그대로다() {
        Member active = social();
        save(active, "active-phone");

        assertThat(cleanup.run()).isZero();

        assertThat(pushTokenRepository.findAllByMemberIdIn(List.of(active.getId())))
                .extracting(PushToken::getToken)
                .containsExactly("active-phone");
    }

    private Member social() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "지수", UUID.randomUUID()));
    }

    private void save(Member member, String token) {
        pushTokenRepository.save(new PushToken(member.getId(), token, PushPlatform.ANDROID));
    }
}
