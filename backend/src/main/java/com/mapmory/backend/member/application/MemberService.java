package com.mapmory.backend.member.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.event.MemberWithdrawn;
import com.mapmory.backend.member.exception.MemberErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원 자신의 정보를 다룬다.
 *
 * access token은 회원 존재를 확인하지 않으므로(ADR 0009) 트랜잭션 안에서 회원을 조회하고,
 * 없거나 탈퇴했으면 MEMBER_NOT_FOUND로 응답한다. (ADR 0021)
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MemberService(MemberRepository memberRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Member findMe(Long memberId) {
        return findActiveMember(memberId);
    }

    @Transactional
    public Member changeNickname(Long memberId, String nickname) {
        Member member = findActiveMember(memberId);
        member.changeNickname(nickname);
        return member;
    }

    /**
     * 탈퇴한다. 게스트도 같다.
     *
     * 이미 탈퇴했으면 아무것도 하지 않는다(멱등). 처음 탈퇴할 때만 MemberWithdrawn을 발행하고,
     * auth가 같은 트랜잭션에서 refresh 토큰을 모두 폐기한다. 이미 발급된 access token(최대 30분)은 남는다. (ADR 0021)
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = findMember(memberId);
        if (member.isWithdrawn()) {
            return;
        }
        member.withdraw(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new MemberWithdrawn(member.getId(), member.getProvider(), member.getWithdrawnAt()));
    }

    private Member findActiveMember(Long memberId) {
        Member member = findMember(memberId);
        if (!member.isActive()) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
