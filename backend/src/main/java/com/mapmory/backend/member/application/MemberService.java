package com.mapmory.backend.member.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
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
 * 없거나 활동 중이 아니면(탈퇴, 버려진 게스트) INACTIVE_MEMBER(401)로 응답한다. (ADR 0021)
 * 회원을 찾지 못한 것이 아니라 이 토큰으로는 더 쓸 수 없다는 뜻이라, 앱이 로그아웃하도록 401로 둔다. (#448)
 * 관리자가 다른 회원을 찾는 경우는 MEMBER_NOT_FOUND(404)다.
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

    /** 애플 탈퇴가 탈퇴 대상을 확인할 때 사용한다. 탈퇴 대상 규칙은 findWithdrawableMember를 따른다. */
    @Transactional(readOnly = true)
    public Member findForWithdrawal(Long memberId) {
        return findWithdrawableMember(memberId);
    }

    /**
     * 닉네임을 바꾼다.
     *
     * 회원을 잠그고 읽는다. Member는 UPDATE할 때 모든 컬럼을 쓰므로, 잠그지 않으면 읽은 뒤 커밋된 탈퇴·승격을
     * 처음 읽은 값으로 덮어써 되돌린다. 잠그면 늦은 쪽이 기다렸다가 최신 행을 읽는다. (#591, ADR 0028)
     */
    @Transactional
    public Member changeNickname(Long memberId, String nickname) {
        Member member = requireActive(lockMember(memberId));
        member.changeNickname(nickname);
        return member;
    }

    /**
     * 탈퇴한다. 게스트도 같다.
     *
     * 이미 탈퇴했으면 아무것도 하지 않는다(멱등). 버려진 게스트는 탈퇴한 적이 없으므로 401 INACTIVE_MEMBER다.
     * 204 멱등은 본인 탈퇴의 재시도(응답 유실)를 위한 것이다. (#576) 처음 탈퇴할 때만 MemberWithdrawn을 발행하고,
     * auth가 같은 트랜잭션에서 refresh 토큰을 모두 폐기한다. 이미 발급된 access token(최대 30분)은 남는다. (ADR 0021)
     *
     * 애플 회원은 애플 토큰을 먼저 철회해야 하므로 여기서 탈퇴할 수 없다. 재인증을 받는 애플 회원 탈퇴
     * (POST /auth/apple/withdrawal)로 보낸다. 이 경로로 탈퇴하면 철회를 건너뛰게 된다. (ADR 0025)
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = lockWithdrawableMember(memberId);
        if (member.isWithdrawn()) {
            return;
        }
        if (member.getProvider() == AuthProvider.APPLE) {
            throw new BusinessException(MemberErrorCode.APPLE_REAUTHENTICATION_REQUIRED);
        }
        markWithdrawn(member);
    }

    /**
     * 소셜 제공자와의 연결을 이미 끊은 회원을 탈퇴시킨다. 애플 회원 탈퇴가 애플 토큰을 철회한 뒤 부른다. (ADR 0025)
     */
    @Transactional
    public void withdrawUnlinked(Long memberId) {
        Member member = lockWithdrawableMember(memberId);
        if (member.isWithdrawn()) {
            return;
        }
        markWithdrawn(member);
    }

    private void markWithdrawn(Member member) {
        member.withdraw(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new MemberWithdrawn(member.getId(), member.getProvider(), member.getWithdrawnAt()));
    }

    /**
     * 탈퇴 대상 회원을 찾는다. 일반 탈퇴와 애플 탈퇴가 같은 규칙을 쓴다.
     *
     *   - 없는 회원: 401 INACTIVE_MEMBER
     *   - 버려진 게스트: 401 INACTIVE_MEMBER. 탈퇴한 적이 없고, 다른 계정으로 로그인하면서 토큰이 죽었다.
     *   - 이미 탈퇴함: 그대로 돌려준다. 호출부가 아무것도 하지 않고 204로 끝낸다(본인 탈퇴의 재시도).
     *
     * 버려짐을 탈퇴 여부보다 먼저 본다. 예전 버그(#576)로 버려진 뒤 탈퇴된 행도 버려진 게스트라 두 경로 모두 401이다.
     * 정상 흐름에서는 탈퇴한 게스트가 버려지지 않는다(승격·버림은 활동 중인 게스트만 대상).
     */
    private Member findWithdrawableMember(Long memberId) {
        return requireWithdrawable(findMember(memberId));
    }

    /**
     * 탈퇴시킬 회원을 잠그고 읽는다. 규칙은 findWithdrawableMember와 같다.
     *
     * 탈퇴는 MemberWithdrawn으로 refresh를 모두 폐기한다. 회원을 먼저 잠가야 잠그는 순서가 refresh 회전과 같은
     * 회원 → refresh가 된다. 잠그지 않으면 refresh 폐기가 먼저, 회원 UPDATE가 커밋 때 나가 순서가 반대가 되어
     * 회원을 공유 잠금한 채 refresh를 기다리는 회전과 데드락이 난다. (ADR 0028)
     */
    private Member lockWithdrawableMember(Long memberId) {
        return requireWithdrawable(lockMember(memberId));
    }

    private Member requireWithdrawable(Member member) {
        if (member.isAbandoned()) {
            throw new BusinessException(MemberErrorCode.INACTIVE_MEMBER);
        }
        return member;
    }

    private Member findActiveMember(Long memberId) {
        return requireActive(findMember(memberId));
    }

    private Member requireActive(Member member) {
        if (!member.isActive()) {
            throw new BusinessException(MemberErrorCode.INACTIVE_MEMBER);
        }
        return member;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
    }

    private Member lockMember(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
    }
}
