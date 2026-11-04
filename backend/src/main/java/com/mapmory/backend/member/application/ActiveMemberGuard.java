package com.mapmory.backend.member.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터를 만들려는 회원이 활동 중인지 확인한다. 없거나 탈퇴했거나 버려진 회원은 INACTIVE_MEMBER(401)다.
 *
 * <p>access token은 탈퇴해도 만료(최대 30분)까지 유효하다. JWT 필터는 요청마다 회원을 조회하지 않으므로(ADR 0009),
 * 남은 토큰으로 데이터나 비용을 만드는 경로가 서비스 트랜잭션에서 이 가드를 부른다(ADR 0021).
 * {@code @LoginMember}로 받은 회원은 id만 가진 프록시라 여기서 다시 읽는다.
 */
@Component
public class ActiveMemberGuard {

    private final MemberRepository memberRepository;

    public ActiveMemberGuard(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Member require(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
    }

    /**
     * 회원을 공유 잠금(FOR SHARE)하고 활동 중인지 확인한다. 트랜잭션이 끝날 때까지 탈퇴·버림이 기다린다.
     *
     * <p>탈퇴가 커밋된 뒤 회원의 데이터를 지우는 경로가 있으면, 확인만 통과한 요청이 그 뒤에 커밋해 데이터가 남을 수 있다.
     * 이 메서드로 확인하면 탈퇴가 그 요청의 커밋을 기다리므로 정리가 그 데이터까지 지운다. 탈퇴가 먼저면 여기서 기다렸다가 거절한다.
     * 잠금은 트랜잭션이 있어야 유지되므로 트랜잭션 없이 부르면 실패한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Member requireLocked(Long memberId) {
        return memberRepository.findByIdForShare(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
    }
}
