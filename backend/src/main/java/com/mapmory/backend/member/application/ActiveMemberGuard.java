package com.mapmory.backend.member.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import org.springframework.stereotype.Component;
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
}
