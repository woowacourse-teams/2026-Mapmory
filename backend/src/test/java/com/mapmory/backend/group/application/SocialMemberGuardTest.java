package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.member.exception.MemberErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SocialMemberGuardTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final SocialMemberGuard guard = new SocialMemberGuard(new ActiveMemberGuard(memberRepository));

    @Test
    void 활동_중인_소셜_회원은_통과한다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                Member.ofOAuth(AuthProvider.KAKAO, "kakao-1", "지수", UUID.randomUUID())));

        assertThatCode(() -> guard.require(1L)).doesNotThrowAnyException();
    }

    @Test
    void 게스트는_소셜_로그인이_필요하다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                Member.ofGuest("guest-1", "게스트", UUID.randomUUID())));

        assertError(1L, GroupErrorCode.SOCIAL_LOGIN_REQUIRED);
    }

    @Test
    void 제공자가_없는_옛_임시_회원도_소셜_로그인이_필요하다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(Member.of("임시", UUID.randomUUID())));

        assertError(1L, GroupErrorCode.SOCIAL_LOGIN_REQUIRED);
    }

    @Test
    void 탈퇴한_회원은_사용할_수_없는_회원이다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "kakao-1", "지수", UUID.randomUUID());
        member.withdraw(LocalDateTime.of(2026, 10, 26, 12, 0));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertError(1L, MemberErrorCode.INACTIVE_MEMBER);
    }

    @Test
    void 없는_회원은_사용할_수_없는_회원이다() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertError(1L, MemberErrorCode.INACTIVE_MEMBER);
    }

    private void assertError(Long memberId, Object errorCode) {
        assertThatThrownBy(() -> guard.require(memberId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
