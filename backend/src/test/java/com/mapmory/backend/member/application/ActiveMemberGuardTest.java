package com.mapmory.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveMemberGuardTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 3, 0);

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final ActiveMemberGuard guard = new ActiveMemberGuard(memberRepository);

    @Test
    void 활동_중인_회원을_돌려준다() {
        Member member = Member.ofGuest("guest-1", "게스트", UUID.randomUUID());
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThat(guard.require(1L)).isSameAs(member);
    }

    @Test
    void 탈퇴한_회원은_사용할_수_없는_회원이다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "kakao-1", "지수", UUID.randomUUID());
        member.withdraw(NOW);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertInactive(1L);
    }

    @Test
    void 버려진_게스트는_사용할_수_없는_회원이다() {
        Member guest = Member.ofGuest("guest-1", "게스트", UUID.randomUUID());
        guest.abandon(NOW);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(guest));

        assertInactive(1L);
    }

    @Test
    void 없는_회원은_사용할_수_없는_회원이다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertInactive(1L);
    }

    private void assertInactive(Long memberId) {
        assertThatThrownBy(() -> guard.require(memberId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.INACTIVE_MEMBER);
    }
}
